# Sprints — SOA préstamo de equipos

Este documento organiza el trabajo del equipo por sprints cortos, con la entrega fijada para el **viernes 27 de noviembre**. Complementa el [plan del proyecto](plan-proyecto-soa.md) y los [contratos de la API](contratos-api.md).

## 1. Flujo de trabajo con Git

- **`main`:** rama estable. No se trabaja directamente sobre ella.
- **`develop`:** rama de integración. Aquí se juntan los avances de todos.
- **`feature/<modulo>`:** una rama por integrante o por tarea. Ejemplos: `feature/seguridad`, `feature/equipos`, `feature/prestamos`, `feature/devolucion`.
- Cada avance entra a `develop` mediante **pull request**, no con push directo.
- Solo cuando `develop` esté estable y probado, se integra a `main`.
- Pasos detallados en [git-workflow.md](git-workflow.md).

### Pasos para cada integrante

1. `git clone https://github.com/Sax300-cpu/SOA-Proyecto.git`
2. `git checkout develop`
3. `git checkout -b feature/<mi-modulo>`
4. Trabajar y hacer commits pequeños con mensajes claros.
5. `git push -u origin feature/<mi-modulo>`
6. Abrir un *pull request* hacia `develop` y esperar revisión de otro compañero.

## 2. Acuerdos que hay que cerrar en la reunión de arranque

Antes de programar en paralelo, el grupo debe dejar claros estos puntos:

1. **Cuentas de demostración:** quién prepara y documenta un usuario `ADMIN` y uno `USUARIO` (contraseñas de prueba, nunca reales ni publicadas).
2. **Disponibilidad del equipo:** se marca **no disponible al aprobar** un préstamo y **disponible al registrar la devolución**. La regla se implementa en el servicio correspondiente.
3. **Ejemplos de JSON:** definir el cuerpo de las peticiones y respuestas de `POST /api/prestamos` y del login.
4. **Login del cliente:** confirmar el mecanismo (formulario con sesión) y cómo se envían las peticiones protegidas desde JavaScript.
5. **Errores de negocio:** unificar los mensajes y códigos HTTP para errores comunes (equipo no disponible, préstamo no aprobado, etc.).

## 3. Archivos compartidos

- **Luis** es dueño de `Prestamo` y `PrestamoRepository`. **Emilio** coordina con él antes de modificar estos archivos.
- **Kevin** es dueño de `security/` y de la configuración de Spring Security.
- **Manolo** es dueño de `Equipo`, `EquipoRepository` y su controlador.
- Si alguien necesita tocar un archivo ajeno, lo coordina antes con su dueño.

## 4. Calendario

| Fechas | Etapa | Objetivo |
|---|---|---|
| 14–15 nov | Arranque | Cerrar los acuerdos del punto 2 y verificar que todos clonan, compilan y conectan a MySQL. |
| 16–20 nov | Sprint 1 | Completar los módulos del servidor por responsable. |
| 23–25 nov | Sprint 2 | Integrar pantallas Bootstrap con la API y probar el recorrido completo. |
| 26 nov | Cierre | Corregir fallas, comprobar una clonación limpia y actualizar instrucciones. |
| 27 nov | Entrega | Presentar el trabajo final. |

## 5. Sprint 1 — Módulos del servidor (16–20 nov)

**Objetivo:** dejar la API funcionando y probada por módulo.

### Kevin Velasco — Seguridad y cuentas
- Configurar Spring Security con roles `ADMIN` y `USUARIO`.
- Inicio y cierre de sesión, y protección de las rutas de administrador.
- Crear y documentar las cuentas de demostración.
- **Criterio de salida:** un ADMIN accede a las rutas administrativas y un USUARIO recibe 403 al intentarlo.

### Manolo Garcia — Catálogo de equipos
- Servicio y controlador REST de `Equipo`.
- Listado, creación, edición y desactivación.
- **Criterio de salida:** crear, listar, editar y desactivar un equipo desde la API, con validaciones.

### Luis Miranda — Solicitudes y aprobación
- Servicio y controlador REST para solicitar préstamos.
- Listar las solicitudes propias y las pendientes.
- Aprobar y rechazar con las validaciones de estado.
- **Criterio de salida:** un usuario solicita un equipo disponible; un administrador aprueba o rechaza; no se puede solicitar un equipo no disponible.

### Emilio Abril — Entrega, devolución e historial
- Entregar préstamos aprobados y registrar la devolución con observación.
- Historial, respetando qué puede ver cada rol.
- **Criterio de salida:** no se entrega algo no aprobado ni se devuelve algo no entregado; al devolver, el equipo vuelve a estar disponible.

**Salida del sprint:** `develop` contiene los cuatro módulos y compila.

## 6. Sprint 2 — Integración y recorrido completo (23–25 nov)

**Objetivo:** conectar la interfaz Bootstrap con la API y cerrar el flujo.

- Pantallas: login, catálogo, mis solicitudes, pendientes y devolución/historial.
- Verificar permisos desde la interfaz (no solo desde la API).
- Probar el recorrido completo: iniciar sesión como usuario, solicitar equipo, iniciar sesión como administrador, aprobar, entregar, devolver y revisar historial.
- **Criterio de salida:** el recorrido funciona sin intervención manual en la base de datos.

## 6 bis. Mejoras de interfaz y reglas (rama `feature/mejoras-interfaz`)

**Estado:** hecho y subido a la rama `feature/mejoras-interfaz` para que el grupo lo pruebe. **No está en `develop`.** El detalle, los pasos de prueba y las decisiones abiertas están en [plan-mejoras-equipo.md](plan-mejoras-equipo.md).

| Tarea | Estado | Responsable de revisar |
|---|---|---|
| Estilo compartido, escudo FISEI y paleta | Hecho | Todos |
| Panel de inicio por rol (resumen + aprobar/rechazar) | Hecho | Luis, Kevin |
| Catálogo con modal y código de equipo (`LAP-001`) | Hecho | Manolo |
| Historial más limpio y badges de estado | Hecho | Emilio |
| Corregir solicitudes duplicadas del mismo usuario | Pendiente | Luis |
| Límite de solicitudes activas por usuario | Pendiente (decidir número) | Luis |
| Plazo de devolución y estado «Vencido» (sin multas) | Pendiente (decidir días) | Luis, Emilio |

**Criterio de salida:** el grupo prueba la rama con la lista de comprobación del plan, acuerda las decisiones pendientes y se fusiona en `develop` con pull request.

## 7. Cierre y entrega (26–27 nov)

- Corregir fallas detectadas en Sprint 2.
- Clonar el repositorio desde cero y comprobar que arranca y conecta a MySQL.
- Actualizar el `README.md` con las instrucciones de ejecución y las cuentas de demostración.
- Ensayar la exposición: cada integrante explica su módulo y las clases que implementó.

## 8. Definición de terminado

Una tarea está terminada cuando:

1. Su código está integrado en `develop` mediante pull request.
2. El proyecto compila con `.\mvnw.cmd -DskipTests compile`.
3. Respeta los permisos y las reglas de negocio de su módulo.
4. Otro integrante la probó y no encontró fallas.
5. No incluye facturación, cobros ni funciones fuera del alcance.
