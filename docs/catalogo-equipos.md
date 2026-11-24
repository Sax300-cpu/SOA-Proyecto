# Catálogo de equipos — Manolo Garcia

## Alcance e integración

Implementa el catálogo sobre `Equipo` y `EquipoRepository` existentes, sin modificar sus campos ni añadir dependencias. Usa Spring Boot, JPA/MySQL, Bootstrap 5.3.9 y JavaScript con `fetch`.

La pantalla está en `/equipos.html`. Requiere una sesión creada mediante la API de Kevin; la página de login sigue pendiente de ese módulo. `/login` actualmente responde 401 y el índice es una pantalla provisional. Este módulo no cambia archivos ni reglas de seguridad de Kevin. Al integrar su login/navegación, enlazar `/equipos.html` después de iniciar sesión. Cerrar sesión vuelve al índice público.

El ADMIN puede registrar, editar y desactivar. El USUARIO ve por defecto solo disponibles y puede consultar también los activos no disponibles. El enlace de solicitud abre la pantalla existente de Luis: allí se debe introducir el ID mostrado en el catálogo. No se modifica su formulario.

## API

| Método | Ruta | Resultado | Rol |
|---|---|---|---|
| GET | `/api/equipos` | Activos, incluidos los reservados | ADMIN, USUARIO |
| GET | `/api/equipos?disponibles=true` | Activos y disponibles | ADMIN, USUARIO |
| GET | `/api/equipos/{id}` | Detalle activo o 404 | ADMIN, USUARIO |
| POST | `/api/equipos` | Alta, 201 | ADMIN |
| PUT | `/api/equipos/{id}` | Edición de datos, 200 | ADMIN |
| GET | `/api/equipos/desactivados` | Lista de equipos desactivados | ADMIN |
| PUT | `/api/equipos/{id}/desactivar` | Desactivación, 200 | ADMIN |
| PUT | `/api/equipos/{id}/activar` | Reactivación, 200 | ADMIN |

Cuerpo de alta (el `prefijo` es opcional) y de edición (el `prefijo` se ignora):

```json
{"nombre":"Cable HDMI","descripcion":"2 metros","prefijo":"CAB"}
```

- **Plazo de devolución:** no se define en el catálogo. Lo fija el administrador al **aprobar** cada préstamo (ver [contratos-api.md](contratos-api.md)).
- **Código de inventario:** lo arma el servidor como `PREFIJO-NÚMERO` (ej. `CAB-010`).
  - El prefijo son 3 letras (A-Z). Si el cliente no lo envía, se toman las 3 primeras letras del nombre sin tildes ni símbolos (`Cámara web` → `CAM`); si el nombre tiene menos de 3 letras, se usa `EQP`. Un prefijo enviado que no tenga exactamente 3 letras responde 400.
  - El número es correlativo **por prefijo**: si ya existe `CAB-009`, el siguiente cable es `CAB-010`. Los equipos desactivados también cuentan para no repetir códigos. Cada prefijo empieza en `001`.
  - El código no cambia al editar el equipo, ni siquiera si cambia el nombre.
  - Si dos altas simultáneas calculan el mismo código, la segunda responde 409 y se puede reintentar.
  - Los equipos antiguos sin código reciben uno de respaldo (`EQUIPO-001`, a partir de su ID) al arrancar la aplicación o al editarlos.
- Nombre obligatorio, no solo espacios, máximo 50 caracteres después de quitar espacios externos.
- Descripción opcional, máximo 200 caracteres después de quitar espacios externos.
- Alta establece `activo=true` y `disponible=true`. El cliente no puede cambiar ID, activo ni disponibilidad en alta/edición.
- La disponibilidad la controlan los servicios de préstamo: la aprobación reserva y la devolución libera. Editar el catálogo nunca libera un equipo reservado.
- Desactivar conserva el registro y la disponibilidad actual. Puede repetirse. Las consultas de catálogo excluyen los inactivos y su detalle responde 404; el historial sigue conservando su `equipoId`.
- Se permite desactivar durante un préstamo: su devolución sigue siendo posible y no reactiva el equipo.
- No existe borrado físico. Un equipo desactivado se puede **activar** de nuevo (`PUT /api/equipos/{id}/activar`, repetible): vuelve al catálogo y queda disponible, salvo que esté reservado o en manos de un usuario (préstamo `APROBADO` o `ENTREGADO`) tenga un daño sin resolver o esté en mantenimiento; en esos casos queda activo pero no disponible. En la pantalla, el ADMIN lo ve con el interruptor «Ver desactivados».
- Edición y desactivación reutilizan `findByIdForUpdate` dentro de una transacción, como los módulos de préstamos.
- Seguridad y CSRF quedan en la configuración existente. El cliente obtiene un token nuevo de `/api/auth/csrf` antes de cada escritura.
- Errores JSON con `error`: 400 datos inválidos; 401 sin sesión; 403 sin permiso/CSRF; 404 inexistente o inactivo; 409 código repetido.

## Comprobación manual

Con JDK 25, MySQL 8 y una sesión iniciada mediante la API existente:

1. Como ADMIN, abrir `/equipos.html`, registrar un equipo (botón «Registrar equipo», abre un modal con nombre, prefijo sugerido y descripción), editar su nombre y comprobar el resultado al actualizar.
2. Registrar dos cables seguidos (`CAB-001` y `CAB-002`) y un proyector (`PRO-001`); probar un prefijo de 2 o 4 letras (400), nombre vacío, más de 50 caracteres y descripción de más de 200. La API debe responder 400, aunque se omitan las validaciones del navegador.
3. Como USUARIO, comprobar que no se muestra el formulario administrativo y que el listado inicial solo contiene disponibles. Intentar escrituras directamente: 403.
4. Solicitar el equipo, aprobarlo como ADMIN y actualizar el catálogo. Debe quedar no disponible. Editar su nombre no debe liberarlo.
5. Entregar, desactivar y devolver. Debe conservarse el préstamo en el historial y el equipo debe seguir fuera del catálogo.
6. Repetir la desactivación: 200. Consultar el detalle inactivo: 404. Quitar sesión: GET API 401. Escribir sin CSRF: 403.
7. Probar descripción con caracteres HTML: se debe mostrar como texto, sin ejecutarse.

## Validación realizada el 17 de noviembre de 2026

- JDK 25 y MySQL 8.0.44 aislado, sin cambiar `pom.xml` ni `application.properties`.
- Suite completa: 33 pruebas correctas (incluidas siete nuevas de `EquipoService`).
- Compilación requerida por el flujo del equipo: `./mvnw.cmd -DskipTests compile`, correcta.
- 23 comprobaciones HTTP correctas con persistencia, sesiones, permisos y CSRF reales, incluido solicitar → aprobar → entregar → desactivar → devolver y conservar historial.
- Revisión en navegador con sesión real: alta, edición y desactivación como ADMIN; formulario administrativo oculto, filtro inicial de disponibles y enlace de solicitud como USUARIO. Para esta QA se usó un auxiliar temporal de acceso fuera del repositorio que consume la API existente, sin implementar ni publicar una pantalla de login.
- Bootstrap verificado visualmente y hash SHA-384 contrastado con el archivo del CDN.
- Verificación de sintaxis JavaScript y de espacios con `git diff --check`.

Pendiente para la integración general: interfaz de login/navegación de Kevin y revisión de otro integrante antes de fusionar el PR. En `PrestamoService.rechazar` existe una asignación de `disponible=true` que podría liberar un equipo reservado por otra solicitud; se señala al módulo de Luis y no se modifica aquí.
