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
| bloqueado | Boolean | `true` = cuenta bloqueada: no inicia sesión ni solicita préstamos (nulo = no bloqueada) |

### Equipo (`Equipo`)
| Campo | Tipo | Notas |
|---|---|---|
| id | Long | Autogenerado |
| codigo | String | Código de inventario único `PREFIJO-NÚMERO` (ej. `CAB-010`), generado por el servidor: 3 letras del nombre (o del `prefijo` enviado) y número correlativo por prefijo |
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
| plazoHoras | Integer | Plazo de devolución en horas (1 a 720) que fija el ADMIN al aprobar; por defecto 72 (3 días). Empieza a contar al entregar |
| fechaLimite | LocalDateTime | Opcional; se calcula al entregar: `fechaEntrega` + el plazo en horas (el enviado al entregar o, si no, `plazoHoras`) |
| fechaDevolucion | LocalDateTime | Se llena al devolver |
| observacion | String | Opcional (máx. 300); observación de la devolución. Obligatoria si el equipo vuelve con detalles o dañado |
| observacionEntrega | String | Opcional (máx. 300); cómo salía el equipo al entregarlo |
| condicionDevolucion | String | `BUEN_ESTADO`, `CON_DETALLES` o `DANADO`; se llena al devolver (se guarda como texto) |
| incidenciaAtendida | Boolean | Solo con `CON_DETALLES` o `DANADO`: `false` hasta que el ADMIN la resuelve, luego `true` |
| enMantenimiento | Boolean | `true` mientras el equipo de una incidencia abierta está en mantenimiento (fuera del catálogo) |

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
| PUT | /api/prestamos/{id}/aprobar | ADMIN | Cambiar a APROBADO fijando el plazo de devolución |
| PUT | /api/prestamos/{id}/rechazar | ADMIN | Cambiar a RECHAZADO |
| PUT | /api/prestamos/{id}/entregar | ADMIN | Cambiar a ENTREGADO |
| PUT | /api/prestamos/{id}/devolver | ADMIN | Cambiar a DEVUELTO (con estado del equipo y observación) |
| PUT | /api/prestamos/{id}/incidencia | ADMIN | Resolver la incidencia de un equipo devuelto con detalles o dañado |
| PUT | /api/prestamos/{id}/anular | ADMIN | Anular un préstamo aprobado que aún no se entregó (queda RECHAZADO) |
| GET | /api/usuarios | ADMIN | Listar usuarios (sin contraseña) |
| POST | /api/usuarios | ADMIN | Crear una cuenta (USUARIO o ADMIN) |
| PUT | /api/usuarios/{id}/bloquear | ADMIN | Bloquear una cuenta de USUARIO |
| PUT | /api/usuarios/{id}/desbloquear | ADMIN | Desbloquear una cuenta |
| GET | /api/prestamos | ADMIN | Historial completo (filtro opcional por estado) |

`POST /api/prestamos` recibe JSON con `equipoId`; el `usuarioId` se toma de la sesión autenticada. La API devuelve `409` si el equipo no está disponible, si el usuario ya tiene una solicitud o préstamo activo para ese equipo, si alcanzó el límite de 3 activos (`prestamos.limite-activos`), o si la solicitud ya no está en estado `SOLICITADO` al aprobarla o rechazarla.

### Entrega, devolución e historial (Emilio)

- `PUT /api/prestamos/{id}/entregar`: cuerpo opcional `{"observacion":"Con un rayón en la tapa","plazoHoras":2}`;
  exige `APROBADO`, registra `fechaEntrega`, `fechaLimite` y la observación de entrega (opcional, máx. 300), y cambia
  a `ENTREGADO`. El plazo es `plazoHoras` si llega (1 a 720, si no 400) y, si no, el que se fijó al aprobar
  (`prestamos.dias-plazo` × 24 si el préstamo no tiene). Es solo informativo, sin multas. No entrega equipos desactivados.
- `PUT /api/prestamos/{id}/aprobar`: cuerpo opcional `{"plazoHoras":2}` (1 a 720 horas; fuera de rango responde 400 y no
  cambia nada). El ADMIN fija el plazo de devolución al aprobar; si no lo envía se usa el predeterminado de 72 horas
  (`prestamos.dias-plazo`). El plazo se guarda en el préstamo y **empieza a contar al entregar**. En la pantalla se pide
  en un diálogo con número y unidad (horas o días) y atajos de 2 h, 3 h, 1 día y 3 días.
- `PUT /api/prestamos/{id}/anular`: cuerpo opcional `{"motivo":"No fue a recogerlo"}` (máx. 300). Solo para `APROBADO`
  (409 en otro estado): el préstamo pasa a `RECHAZADO` con el motivo en `observacion` (si no hay motivo, «Anulado por
  el administrador») y el equipo reservado vuelve a estar disponible.
- `POST /api/usuarios` (solo ADMIN): cuerpo `{"nombre","username","password","rol"}` con `rol` USUARIO (por defecto) o
  ADMIN (otro valor: 400). Usa las mismas reglas del registro público: todos los campos obligatorios, nombre de hasta 50,
  usuario sin espacios y de hasta 30, contraseña de 8 caracteres y hasta 72 bytes en UTF-8. Responde 201 con
  `{id, nombre, username, rol, bloqueado}` (nunca la contraseña, que se guarda cifrada con BCrypt) o 409 si el usuario ya existe.
- `GET /api/usuarios` devuelve `[{id, nombre, username, rol, bloqueado}]`. `PUT /api/usuarios/{id}/bloquear` y
  `/desbloquear` (repetibles) cambian el bloqueo; las cuentas ADMIN no se pueden bloquear (400). Una cuenta bloqueada no
  inicia sesión (401 «Tu cuenta está bloqueada») y, si ya tenía sesión, recibe 403 al solicitar un préstamo.
- `PUT /api/prestamos/{id}/devolver`: cuerpo opcional
  `{"observacion":"Pantalla rota","condicion":"DANADO"}`; exige `ENTREGADO`, registra
  `fechaDevolucion` y cambia a `DEVUELTO`. La `condicion` es `BUEN_ESTADO` (por defecto), `CON_DETALLES`
  o `DANADO`; con las dos últimas la observación es obligatoria (400 si falta) y queda una **incidencia abierta**
  (`incidenciaAtendida=false`) que el usuario ve como alerta. El equipo vuelve a estar disponible, salvo con
  `DANADO`: queda no disponible hasta que el ADMIN resuelva la incidencia.
  La observación se recorta y admite hasta 300 caracteres. No se cambia el campo `activo` del equipo ni se registran cobros.
- `PUT /api/prestamos/{id}/incidencia`: cuerpo `{"accion":"..."}`; solo para préstamos `DEVUELTO` con incidencia abierta
  (409 si no la hay o ya se atendió). Acciones permitidas (otra acción: 400):
  - `CON_DETALLES`: `ATENDIDA` (cierra el aviso, el equipo sigue disponible), `MANTENIMIENTO` o `BAJA`.
  - `DANADO`: `REPARADO` (el equipo vuelve al catálogo), `MANTENIMIENTO` o `BAJA`.
  - Si ya está en mantenimiento: `REPARADO` o `BAJA`.
  - `MANTENIMIENTO` deja el equipo no disponible y la incidencia abierta (`enMantenimiento=true`). `BAJA` desactiva el equipo
    (se puede reactivar desde «Ver desactivados»). `ATENDIDA`, `REPARADO` y `BAJA` cierran la incidencia.
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
