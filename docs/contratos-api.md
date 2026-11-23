# Contratos del proyecto — SOA préstamo de equipos

Este documento fija los acuerdos técnicos que los cuatro integrantes deben respetar para trabajar en paralelo sin chocar. Si algo cambia, se actualiza aquí y se avisa al grupo.

## 1. Entidades y campos

Las entidades están en `src/main/java/com/soa/proyecto/model/` y los repositorios en `.../repository/`.

### Usuario (`Usuario`)
| Campo | Tipo | Notas |
|---|---|---|
| id | Long | Autogenerado |
| nombre | String | Obligatorio |
| username | String | Único, obligatorio |
| password | String | Obligatorio, se guarda encriptado |
| rol | Rol (enum) | ADMIN o USUARIO |

### Equipo (`Equipo`)
| Campo | Tipo | Notas |
|---|---|---|
| id | Long | Autogenerado |
| codigo | String | Código de inventario autogenerado (`EQUIPO-001`, `EQUIPO-002`, ...), único |
| nombre | String | Obligatorio |
| descripcion | String | Opcional |
| disponible | Boolean | Obligatorio |
| activo | Boolean | Obligatorio; false = desactivado |

### Préstamo (`Prestamo`)
| Campo | Tipo | Notas |
|---|---|---|
| id | Long | Autogenerado |
| usuarioId | Long | Referencia a Usuario.id |
| equipoId | Long | Referencia a Equipo.id |
| estado | EstadoPrestamo (enum) | Ver estados abajo |
| fechaSolicitud | LocalDateTime | Obligatorio |
| fechaAprobacion | LocalDateTime | Se llena al aprobar |
| fechaEntrega | LocalDateTime | Se llena al entregar |
| fechaDevolucion | LocalDateTime | Se llena al devolver |
| observacion | String | Opcional, para daño en devolución |

> Las relaciones se manejan por id (`usuarioId`, `equipoId`) para mantener el modelo simple y evitar problemas al convertir a JSON. No se usan `@OneToMany` ni `@ManyToOne`.

## 2. Estados de un préstamo

```
SOLICITADO -> APROBADO -> ENTREGADO -> DEVUELTO
SOLICITADO -> RECHAZADO (fin)
```

- Solo el administrador cambia de SOLICITADO a APROBADO o RECHAZADO.
- Solo el administrador cambia de APROBADO a ENTREGADO.
- Solo el administrador cambia de ENTREGADO a DEVUELTO.
- No se permite saltar estados (por ejemplo, devolver sin entregar).

## 3. API REST propuesta

Base: `/api`. El equipo puede ajustar estas rutas si lo acuerda y lo refleja aquí.

### Equipos (responsable: Manolo Garcia)
| Método | Ruta | Acceso | Descripción |
|---|---|---|---|
| GET | /api/equipos | ADMIN, USUARIO | Lista equipos activos |
| GET | /api/equipos/{id} | ADMIN, USUARIO | Detalle de un equipo |
| POST | /api/equipos | ADMIN | Crear equipo |
| PUT | /api/equipos/{id} | ADMIN | Editar equipo |
| PUT | /api/equipos/{id}/desactivar | ADMIN | Desactivar equipo |

### Préstamos (responsables: Luis Miranda y Emilio Abril)
| Método | Ruta | Acceso | Descripción |
|---|---|---|---|
| POST | /api/prestamos | USUARIO | Solicitar préstamo de un equipo disponible |
| GET | /api/prestamos/mios | USUARIO | Listar las solicitudes del usuario autenticado |
| GET | /api/prestamos/pendientes | ADMIN | Listar solicitudes en estado SOLICITADO |
| PUT | /api/prestamos/{id}/aprobar | ADMIN | Cambiar a APROBADO |
| PUT | /api/prestamos/{id}/rechazar | ADMIN | Cambiar a RECHAZADO |
| PUT | /api/prestamos/{id}/entregar | ADMIN | Cambiar a ENTREGADO |
| PUT | /api/prestamos/{id}/devolver | ADMIN | Cambiar a DEVUELTO (con observación) |
| GET | /api/prestamos | ADMIN | Historial completo (filtro opcional por estado) |

`POST /api/prestamos` recibe JSON con `equipoId`; el `usuarioId` se toma de la sesión autenticada. La API devuelve `409` si el equipo no está disponible o si la solicitud ya no está en estado `SOLICITADO` al aprobarla o rechazarla.

### Entrega, devolución e historial (Emilio)

- `PUT /api/prestamos/{id}/entregar`: sin cuerpo; exige `APROBADO`, registra
  `fechaEntrega` y cambia a `ENTREGADO`. No entrega equipos desactivados.
- `PUT /api/prestamos/{id}/devolver`: cuerpo opcional
  `{"observacion":"Pantalla rayada"}`; exige `ENTREGADO`, registra
  `fechaDevolucion`, cambia a `DEVUELTO` y marca el equipo disponible.
  La observación es opcional, se recortan espacios y admite hasta 300 caracteres.
  No se cambia el campo `activo` del equipo ni se registran cobros.
- `GET /api/prestamos?estado=DEVUELTO`: historial administrativo; el filtro es opcional.
- `GET /api/historial/mio?estado=DEVUELTO`: historial del `USUARIO` autenticado;
  el filtro es opcional. No recibe un identificador de usuario del cliente.
  Esta ruta complementa `/api/prestamos/mios`, cuya implementación corresponde a Luis,
  y evita el patrón administrativo `/api/prestamos/**` de la seguridad de Kevin.
- Las cuatro rutas responden JSON y devuelven 200 al completarse. Errores de este
  módulo: 400 para estados/datos inválidos, 401 sin sesión, 403 sin el rol requerido,
  404 para préstamo/equipo inexistente. El cuerpo de errores del controlador es
  `{"error":"mensaje"}`; la seguridad puede rechazar una petición antes del controlador.
- Historial ordenado por fecha de solicitud descendente y, en empates, id descendente.
- Las escrituras usan una transacción y bloqueo de préstamo/equipo: una segunda
  entrega o devolución del mismo préstamo debe rechazarse por su estado.

Pantalla del módulo: `/devoluciones.html`, con la sesión y CSRF del módulo de Kevin.
Detalles y comprobaciones manuales: [entrega-devolucion.md](entrega-devolucion.md).

### Autenticación (responsable: Kevin Velasco)

Sesión del navegador con Spring Security; sin JWT ni OAuth.

| Método | Ruta | Acceso | Descripción |
|---|---|---|---|
| POST | /api/auth/login | Público | Inicia sesión. Recibe `username` y `password` (form-encoded). |
| POST | /api/auth/registro | Público | Registra un usuario nuevo con rol `USUARIO`. Recibe JSON `{nombre, username, password}`. |
| POST | /api/auth/logout | Autenticado | Cierra la sesión. |
| GET | /api/auth/me | Público | Devuelve el usuario actual o 401 si no hay sesión. |

- El cliente y la API viven en el mismo servidor; la cookie de sesión viaja sola.
- `POST /api/auth/login` responde `200` con `{username, rol}` en éxito y `401` con `{error}` si falla.
- `POST /api/auth/registro` responde `201` con `{username, nombre, rol}` (siempre `USUARIO`), `400` si faltan datos o la contraseña no cumple el mínimo de 8 caracteres y el máximo de 72 bytes en UTF-8, y `409` si el `username` ya existe, incluso con registros simultáneos.
- `GET /api/auth/me` responde `{username, nombre, rol}` (sin contraseña) o `401`.
- Las rutas de ADMIN rechazan a USUARIO con `403`.
- CSRF: Spring Security envía la cookie `XSRF-TOKEN`; el cliente debe devolverla en el encabezado `X-XSRF-TOKEN` en POST/PUT/DELETE.

### Cuentas de demostración (solo desarrollo)

| Usuario | Contraseña | Rol |
|---|---|---|
| admin | admin123 | ADMIN |
| usuario | usuario123 | USUARIO |

Se crean automáticamente la primera vez que se ejecuta la aplicación. Las contraseñas se guardan encriptadas. No usar contraseñas reales de los integrantes.

## 4. Convenciones de código

- Paquetes: `model`, `repository`, `service`, `controller`, `security`.
- Una responsabilidad por método; la lógica de negocio va en `@Service`, no en el controlador ni en el HTML.
- Respuestas JSON desde controladores REST (`@RestController`).
- Nombres de clases y métodos en español, descriptivos.
- Errores de reglas de negocio con mensajes claros y códigos HTTP adecuados: 400 datos inválidos, 403 sin permiso, 404 no encontrado, 200/201 éxito.
- **Nunca devolver el `password` en las respuestas JSON.** Usar un DTO o la anotación adecuada para ocultarlo.
- Antes de tocar un archivo de otro compañero, coordinar con el dueño del módulo.
