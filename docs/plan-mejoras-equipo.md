# Plan de mejoras — rama `feature/mejoras-interfaz`

Resumen de lo que se hizo el 23 de noviembre sobre `develop` (f8b0208), qué hay que probar y qué decisiones quedan abiertas para el grupo. **Esta rama no se ha fusionado en `develop`**: primero se prueba y, si el grupo está de acuerdo, se abre un pull request.

## 1. Cómo probarla

```powershell
git fetch origin
git checkout feature/mejoras-interfaz
```

1. Iniciar MySQL desde XAMPP.
2. Si ya tenías equipos creados, darles código (la columna nueva `equCodigo` queda vacía). En phpMyAdmin, base `soa_prestamos`:
   ```sql
   UPDATE equipos SET equCodigo = CONCAT('EQ-', LPAD(equId, 3, '0')) WHERE equCodigo IS NULL;
   ```
3. `.\mvnw.cmd test` (deben pasar 35 pruebas) y luego `.\mvnw.cmd spring-boot:run`.
4. Abrir `http://localhost:8080` con las cuentas de demostración: `admin / admin123` (ADMIN) y `usuario / usuario123` (USUARIO).

## 2. Qué cambió y a quién le toca revisarlo

| Área | Cambio | Revisa |
|---|---|---|
| Estilo general | Nuevo `css/estilo.css`: paleta azul tinta + marfil + dorado, tablas, tarjetas, modales. Se enlaza en todas las páginas junto con Bootstrap Icons. | Todos |
| Login y registro | Fondo blanco con el escudo de la FISEI (`static/img`). Barra superior con el escudo. | Kevin |
| Inicio (`index.html`) | Ya no repite el menú: es un **panel de resumen**. Admin: equipos disponibles, pendientes, aprobados por entregar, entregados sin devolver, y las 5 solicitudes más recientes con **Aprobar/Rechazar**. Usuario: equipos disponibles, esperando aprobación, listos para retirar, en su poder, y sus últimos préstamos. | Luis, Kevin |
| Catálogo | El formulario de registro/edición pasó a un **modal** (`<dialog>`). Barra de búsqueda y filtros ordenada y contador de equipos. | Manolo |
| Código de equipo | Campo `codigo` en `Equipo` autogenerado en el servidor (`EQUIPO-001`, `EQUIPO-002`, ...). Se asigna también automáticamente al arrancar a equipos antiguos sin código. Se muestra en catálogo, historial, pendientes y mis solicitudes en lugar de `#id`. | Manolo, Luis, Emilio |
| Historial | Aviso verde eliminado, tabla con el mismo estilo, filtro que se aplica al cambiar, fechas más cortas. | Emilio |
| Estados | Badges de color (`etiquetaEstado` en `js/encabezado.js`). | Todos |

### Cambio de contrato de la API
`POST /api/equipos` y `PUT /api/equipos/{id}` mantienen el contrato original de entrada (`nombre` y `descripcion`), y la respuesta incluye el `codigo` autogenerado (`EQUIPO-001`, `EQUIPO-002`, ...):

```json
{"nombre":"Laptop HP","descripcion":"Para clases"}
```

Detalles en [catalogo-equipos.md](catalogo-equipos.md) y [contratos-api.md](contratos-api.md).

### Archivos de otros integrantes que se tocaron
Siguiendo la regla del equipo, se avisa: `Equipo`, `EquipoRepository`, `EquipoService`, `EquipoRequest` (Manolo); `equipos.html/js` (Manolo); `mis-solicitudes.html` y `admin-aprobaciones.html` (Luis, solo estilo y nombre del equipo); `devoluciones.html/js/css` (Emilio); `login.html`, `registro.html` y `encabezado.js` (Kevin). No se tocaron `Prestamo`, `PrestamoService` ni `PrestamoRepository`.

## 3. Lista de pruebas para los compañeros

- [ ] Login como ADMIN y como USUARIO; el inicio muestra su panel.
- [ ] Admin: registrar un equipo con código `LAP-010`; repetirlo (error 409 dentro del modal); probar `ABC` o `KSDKAKDKASKAKD` (error de formato).
- [ ] Admin: editar un equipo desde el modal y comprobar que el código aparece en el catálogo.
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

### Orden sugerido
1. Probar esta rama y corregir lo que encuentren.
2. 4.1 (corregir duplicados), por ser un error.
3. 4.2 y 4.3, si el grupo las aprueba.
4. Fusionar en `develop` con pull request y luego `develop` → `main`.
