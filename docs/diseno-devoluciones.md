# Plan de Diseño — Entregas, devoluciones e historial

## 1. Usuario y contexto
- Administrador que entrega y recibe equipos desde un navegador de escritorio o móvil.
- Usuario que consulta las fechas, el estado y las observaciones de sus préstamos.
- Ambos usan la sesión existente; la pantalla no crea cuentas ni reemplaza el login.

## 2. Modelo conceptual y metáfora
- Metáfora: registro de préstamos, con una fila por operación y su recorrido visible.
- Aprobado permite entregar; entregado permite devolver; devuelto conserva el registro.
- Solo se muestra la acción válida para cada fila. Los estados se expresan con texto.

## 3. Arquitectura de información
- Inicio → `/devoluciones.html` → filtro por estado → historial.
- ADMIN: historial general, usuario asociado y acciones.
- USUARIO: historial propio sin controles administrativos.
- Diálogo de confirmación → entrega o devolución → actualización de la tabla.

## 4. Flujos de usuario
- Administrador localiza un aprobado, selecciona Registrar entrega y confirma.
- Al recibir el equipo, selecciona Registrar devolución, escribe una observación
  opcional y confirma. Puede cancelar antes de guardar.
- El usuario consulta su historial y filtra por estado.
- Si falla la consulta, se retiran acciones antiguas y se permite reintentar.
- Si falla una escritura, el diálogo conserva la observación y explica el error.
- Durante el guardado se desactivan botones y el cierre para evitar duplicaciones.

## 5. Wireframes (spec por pantalla)
- Encabezado: enlace al inicio y nombre/rol de la sesión.
- Título, explicación breve y mensaje de estado persistente.
- Filtro etiquetado y botón Consultar.
- Tabla adaptable con desplazamiento horizontal: préstamo, equipo, usuario (ADMIN),
  estado, fechas, observación y acción (ADMIN). Ejemplo: #1, equipo #3, APROBADO.
- Sin filas: mensaje explícito. Cargando: aviso y formulario temporalmente bloqueado.
- Diálogo: título de operación, identificadores, observación opcional solo al devolver,
  error local, Cancelar y Confirmar. El diálogo nativo administra foco y teclado.

## 6. Justificación HCI
- Nielsen: estado visible; acciones por estado previenen errores; confirmación y
  cancelación dan control; mensajes permiten corregir y reintentar.
- Gestalt: controles relacionados agrupados, fechas alineadas y acciones junto al registro.
- Fitts: controles con altura mínima de 44 px y separación entre cancelar y confirmar.
- POUR / WCAG 2.2: HTML semántico, etiquetas, encabezados de tabla, región desplazable
  enfocable, foco visible, texto de estado y avisos anunciados. Bootstrap aporta estilos
  estándar; teclado, contraste y lector de pantalla requieren verificación en el entorno integrado.
- ISO 9241-11: medir éxito al entregar/devolver, errores de estado, tiempo por operación
  y satisfacción en la comprobación con otro compañero. No se afirman resultados medidos.

## 7. Handoff
- No se solicita prototipo de Figma. Esta especificación guía la pantalla Bootstrap.
- Implementación: `devoluciones.html`, `css/devoluciones.css` y `js/devoluciones.js`.
- Integración: login, sesión y endpoint CSRF existentes en la rama de Kevin.
