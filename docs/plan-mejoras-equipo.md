# Plan de mejoras — rama `feature/mejoras-interfaz`

Resumen de lo que se hizo el 23 de noviembre sobre `develop` (f8b0208), qué hay que probar y qué decisiones quedan abiertas para el grupo. **Esta rama no se ha fusionado en `develop`**: primero se prueba y, si el grupo está de acuerdo, se abre un pull request.

## 1. Cómo probarla

```powershell
git fetch origin
git checkout feature/mejoras-interfaz
```

1. Iniciar MySQL desde XAMPP.
2. Los equipos que ya existan reciben un código de respaldo (`EQUIPO-001`…) al arrancar la aplicación; no hace falta ningún SQL.
3. `.\mvnw.cmd test` (deben pasar 45 pruebas) y luego `.\mvnw.cmd spring-boot:run`.
4. Abrir `http://localhost:8080` con las cuentas de demostración: `admin / admin123` (ADMIN) y `usuario / usuario123` (USUARIO).

## 2. Qué cambió y a quién le toca revisarlo

| Área | Cambio | Revisa |
|---|---|---|
| Estilo general | Nuevo `css/estilo.css`: paleta azul tinta + marfil + dorado, tablas, tarjetas, modales. Se enlaza en todas las páginas junto con Bootstrap Icons. | Todos |
| Login y registro | Fondo blanco con el escudo de la FISEI (`static/img`). Barra superior con el escudo. | Kevin |
| Inicio (`index.html`) | Ya no repite el menú: es un **panel de resumen**. Admin: equipos disponibles, pendientes, aprobados por entregar, entregados sin devolver, y las 5 solicitudes más recientes con **Aprobar/Rechazar**. Usuario: equipos disponibles, esperando aprobación, listos para retirar, en su poder, y sus últimos préstamos. | Luis, Kevin |
| Catálogo | El formulario de registro/edición pasó a un **modal** (`<dialog>`). Barra de búsqueda y filtros ordenada y contador de equipos. | Manolo |
| Código de equipo | Campo `codigo` en `Equipo`, armado en el servidor como `PREFIJO-NÚMERO` (ej. `CAB-010`): el prefijo son 3 letras sugeridas desde el nombre (editables en el modal) y el número es correlativo por prefijo. Los equipos antiguos reciben `EQUIPO-nnn` al arrancar. Se muestra en catálogo, historial, pendientes y mis solicitudes en lugar de `#id`. | Manolo, Luis, Emilio |
| Historial | Aviso verde eliminado, tabla con el mismo estilo, filtro que se aplica al cambiar, fechas más cortas. | Emilio |
| Estados | Badges de color (`etiquetaEstado` en `js/encabezado.js`). | Todos |

### Cambio de contrato de la API
`POST /api/equipos` recibe `nombre`, `descripcion` y, opcionalmente, `prefijo` (3 letras). `PUT /api/equipos/{id}` ignora el `prefijo`. La respuesta incluye el `codigo` generado:

```json
{"nombre":"Cable HDMI","descripcion":"2 metros","prefijo":"CAB"}
```

Detalles en [catalogo-equipos.md](catalogo-equipos.md) y [contratos-api.md](contratos-api.md).

### Archivos de otros integrantes que se tocaron
Siguiendo la regla del equipo, se avisa: `Equipo`, `EquipoRepository`, `EquipoService`, `EquipoRequest` (Manolo); `equipos.html/js` (Manolo); `mis-solicitudes.html` y `admin-aprobaciones.html` (Luis, solo estilo y nombre del equipo); `devoluciones.html/js/css` (Emilio); `login.html`, `registro.html` y `encabezado.js` (Kevin). No se tocaron `Prestamo`, `PrestamoService` ni `PrestamoRepository`.

## 3. Lista de pruebas para los compañeros

- [ ] Login como ADMIN y como USUARIO; el inicio muestra su panel.
- [ ] Admin: registrar «Cable HDMI» y comprobar que el prefijo se sugiere solo (`CAB`) y que el código es `CAB-001`; registrar otro cable (`CAB-002`) y un proyector (`PRO-001`).
- [ ] Admin: editar un equipo desde el modal; el código se mantiene aunque cambie el nombre y el prefijo ya no se puede tocar.
- [ ] Usuario: solicitar un equipo desde el catálogo.
- [ ] Admin: aprobar y rechazar **desde el panel de inicio**; los números se actualizan.
- [ ] Admin: Historial → Registrar entrega y Registrar devolución.
- [ ] Usuario: el historial y «Mis solicitudes» muestran el código y el nombre del equipo.
- [ ] Probar en el móvil (o con el navegador a 375 px de ancho).

## 4. Pendientes y decisiones (implementados en `feature/mejoras-prestamos`)

Reglas acordadas e implementadas en la rama `feature/mejoras-prestamos` (con límite de **3** activos y plazo de **3 días**):

### 4.1 Solicitudes duplicadas — Implementado
En `PrestamoService.crearSolicitud`, responde `409` si el usuario ya tiene una solicitud activa (`SOLICITADO`, `APROBADO` o `ENTREGADO`) de ese equipo; en el catálogo (`/js/equipos.js`), muestra «Solicitud en curso» desactivado.

### 4.2 Límite de solicitudes por usuario — Implementado (máx. 3)
Máximo **3 solicitudes/préstamos activos** por usuario a la vez (`prestamos.limite-activos=3` en `application.properties`). Al superarlo, responde `409` con un mensaje claro.

### 4.3 Plazo de devolución — Implementado (3 días, sin multas)
Al **entregar** en `DevolucionService.entregar`, se guarda `fechaLimite = hoy + 3 días` (`prestamos.dias-plazo=3` en `application.properties`). Es una columna nueva y opcional (`preFechaLimite`) en `Prestamo`, sin tocar el enum `EstadoPrestamo`.
- **Estado visual «Vencido»:** se calcula en cliente (`ENTREGADO` y `fechaLimite` superada). Se muestra badge rojo en historial, mis solicitudes y panel de inicio (Admin: alerta y «Entregados (N vencidos)»; Usuario: indicador en «En tu poder»).
- **Alcance:** solo informativo. **No hay multas ni cobros**, tal como exige el proyecto.

### 4.4 Cómo devuelve un usuario — Implementado
Se muestra un aviso informativo en `/index.html` (panel de usuario), `/mis-solicitudes.html` y `/devoluciones.html` explicando que la devolución la registra el administrador al recibir el equipo físico.

### Administración (implementado en `feature/codigo-por-prefijo`)
- **Plazo de devolución en horas que fija el administrador al aprobar** (en Pendientes o en el Inicio), con un diálogo y atajos de 2 h, 3 h, 1 día y 3 días; empieza a contar al entregar y se puede ajustar en la entrega (§4.3 ya no es fijo de 3 días).
- **Nuevo usuario:** el administrador crea cuentas (USUARIO o ADMIN) desde la página Usuarios.
- **Incidencias:** página propia (`/incidencias.html`) con resolución: atendida, mantenimiento, reparado o baja; tarjeta en el Inicio e insignias «En mantenimiento»/«Dañado» en el catálogo.
- **Usuarios** (`/usuarios.html`): lista con préstamos activos y vencidos; bloquear y desbloquear cuentas. Los nombres de usuario aparecen en las tablas del administrador en lugar de `#id`.
- **Anular un préstamo aprobado** que no se retiró (queda RECHAZADO y libera el equipo).
- **Filtros en el historial:** texto (equipo, código o usuario), fechas y solo vencidos.
- **Equipos desactivados:** se ven con «Ver desactivados» y se pueden activar de nuevo.

### Orden sugerido
1. Probar esta rama y corregir lo que encuentren.
2. 4.1 (corregir duplicados), por ser un error.
3. 4.2 y 4.3, si el grupo las aprueba.
4. Fusionar en `develop` con pull request y luego `develop` → `main`.
