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
| PUT | `/api/equipos/{id}/desactivar` | Desactivación, 200 | ADMIN |

Cuerpo de alta/edición:

```json
{"nombre":"Proyector","descripcion":"Para aulas"}
```

- Nombre obligatorio, no solo espacios, máximo 50 caracteres después de quitar espacios externos.
- Descripción opcional, máximo 200 caracteres después de quitar espacios externos.
- Alta establece `activo=true` y `disponible=true`. El cliente no puede cambiar ID, activo ni disponibilidad en alta/edición.
- La disponibilidad la controlan los servicios de préstamo: la aprobación reserva y la devolución libera. Editar el catálogo nunca libera un equipo reservado.
- Desactivar conserva el registro y la disponibilidad actual. Puede repetirse. Las consultas de catálogo excluyen los inactivos y su detalle responde 404; el historial sigue conservando su `equipoId`.
- Se permite desactivar durante un préstamo: su devolución sigue siendo posible y no reactiva el equipo.
- No existe borrado físico ni reactivación en este alcance.
- Edición y desactivación reutilizan `findByIdForUpdate` dentro de una transacción, como los módulos de préstamos.
- Seguridad y CSRF quedan en la configuración existente. El cliente obtiene un token nuevo de `/api/auth/csrf` antes de cada escritura.
- Errores JSON con `error`: 400 datos inválidos; 401 sin sesión; 403 sin permiso/CSRF; 404 inexistente o inactivo.

## Comprobación manual

Con JDK 25, MySQL 8 y una sesión iniciada mediante la API existente:

1. Como ADMIN, abrir `/equipos.html`, registrar un equipo, editar su nombre y comprobar el resultado al actualizar.
2. Intentar nombre vacío, más de 50 caracteres y descripción de más de 200. La API debe responder 400, aunque se omitan las validaciones del navegador.
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
