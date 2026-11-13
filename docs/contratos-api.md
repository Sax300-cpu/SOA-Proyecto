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

### Autenticación (responsable: Kevin Velasco)
Propuesta inicial para no complicar: sesión del navegador con Spring Security.

- El cliente estático y la API viven en el mismo servidor (mismo origen), así la cookie de sesión viaja automáticamente.
- Login por formulario o endpoint propio; cierre de sesión con logout.
- No usar JWT ni OAuth.
- Las rutas de ADMIN deben rechazar las peticiones de USUARIO (código 403).

## 4. Convenciones de código

- Paquetes: `model`, `repository`, `service`, `controller`, `security`.
- Una responsabilidad por método; la lógica de negocio va en `@Service`, no en el controlador ni en el HTML.
- Respuestas JSON desde controladores REST (`@RestController`).
- Nombres de clases y métodos en español, descriptivos.
- Errores de reglas de negocio con mensajes claros y códigos HTTP adecuados: 400 datos inválidos, 403 sin permiso, 404 no encontrado, 200/201 éxito.
- **Nunca devolver el `password` en las respuestas JSON.** Usar un DTO o la anotación adecuada para ocultarlo.
- Antes de tocar un archivo de otro compañero, coordinar con el dueño del módulo.
