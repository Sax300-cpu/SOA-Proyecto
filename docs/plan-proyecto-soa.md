# Plan de trabajo — Sistema SOA para préstamo de equipos tecnológicos

## 1. Propósito

Construir en equipo un sistema web para administrar el préstamo y la devolución de equipos tecnológicos (por ejemplo, laptops o proyectores). El sistema tendrá dos perfiles mínimos: **ADMIN** y **USUARIO**. No manejará facturación, pagos ni cobros.

Este documento organiza el alcance, las reglas comunes y las responsabilidades de Kevin Velasco, Manolo Garcia, Luis Miranda y Emilio Abril. La exposición existente se usa únicamente como referencia; no se debe modificar.

## 1.1 Requisitos indicados por el ingeniero

- Cliente/interfaz con Bootstrap; el equipo ya ha trabajado con Bootstrap.
- Servidor únicamente con Spring Boot.
- Seguridad con Spring Security y al menos dos perfiles: ADMIN y otro usuario.
- El proyecto final no debe incluir facturación.
- Se debe entregar la propuesta del proyecto y el tema no debe repetirse entre grupos.
- Se indicó trabajar con controladores (`Controller`) y que el cliente puede usar varias tecnologías. El equipo confirma que ya recibió una explicación de REST y decide utilizarlo.
- Fecha mencionada para la entrega: viernes 27 de noviembre.

Estos requisitos se consideran definidos; no hace falta volver a preguntarle al ingeniero si se puede usar Bootstrap, Spring Boot o Spring Security.

## 2. Análisis de la exposición `grupouno`

Se revisaron `pom.xml`, el código bajo `src/main/java`, la vista `index.html` y `application.properties`. La exposición presenta un CRUD de estudiantes y estas convenciones:

| Área | Elementos encontrados | Uso como referencia |
|---|---|---|
| Arranque | `@SpringBootApplication`, método `main` | Mantener el arranque estándar de Spring Boot. |
| Modelo | `@Entity`, `@Table`, `@Id`, `@Column`; Lombok (`@Data`, constructores) | Entidades JPA sencillas, con campos explícitos. |
| Persistencia | `JpaRepository`, `@Repository`, `findAll`, `findById`, `save`, `existsById`, `deleteById` | Repositorios simples; evitar SQL manual si no hace falta. |
| Lógica | `@Service`, constructor con `@Autowired`, métodos de listado/búsqueda/guardar/editar/eliminar | Poner las reglas del negocio en servicios, no en las vistas. |
| Web | `@Controller`, `@RequestMapping`, `@GetMapping`, `@PostMapping`, `@PathVariable`, `Model`, `@ModelAttribute`, redirecciones | Mantener rutas y controladores fáciles de leer. |
| Vistas | Thymeleaf, formulario, tabla y enlaces | Es referencia del CRUD; el proyecto final usará un cliente Bootstrap que consume la API REST. |
| Base de datos | MySQL y configuración en `application.properties` | Conservar la configuración sencilla de desarrollo local. |
| Construcción | Maven y archivo `pom.xml` | No actualizar versiones o agregar dependencias sin acuerdo del equipo. |

**No aparecen implementados en el código de la exposición:** Bootstrap, REST, autenticación, autorización, Spring Security ni pruebas desarrolladas. El equipo aclara que ya ha usado Bootstrap y recibió una explicación de REST. **Spring Security es la nueva incorporación**, recomendada por el ingeniero; se usará solo lo necesario para login y roles.

## 3. Arquitectura y tecnologías propuestas

- **Servidor:** una aplicación Spring Boot; no crear servidores con otros frameworks.
- **Cliente:** HTML, CSS, Bootstrap y JavaScript sencillo. La interfaz llamará a la API REST y mostrará los datos recibidos.
- **Persistencia:** Spring Data JPA y MySQL.
- **Organización del servidor:** paquetes `model`, `repository`, `service` y `controller`, siguiendo la separación aprendida. Los controladores REST exponen operaciones HTTP y JSON; `@Service` contiene reglas de negocio y los repositorios acceden a la base de datos.
- **Seguridad:** Spring Security con inicio de sesión y permisos por rol. Mantener una sesión web sencilla; no usar JWT ni OAuth.
- **SOA — decisión tomada:** implementar una API REST en **una sola aplicación Spring Boot** y hacer que el cliente Bootstrap la consuma. No se crearán microservicios independientes. El uso de `@Service` complementa la organización, pero la interfaz de servicio hacia el cliente será la API REST.
- **Estructura de cliente sugerida:** guardar HTML/CSS/JavaScript en `src/main/resources/static` para que Spring Boot sirva la interfaz y esta llame a la API del mismo servidor. Así se evita desplegar otro servidor o configurar CORS para el alcance inicial.
- **Dependencias nuevas:** revisar entre los cuatro la dependencia necesaria para Spring Security. No añadir librerías por conveniencia sin explicar para qué se necesitan.

## Repositorio del proyecto

- GitHub: [https://github.com/Sax300-cpu/SOA-Proyecto](https://github.com/Sax300-cpu/SOA-Proyecto)
- El repositorio estaba vacío al iniciar el trabajo; este documento se publicará como su primer archivo de planificación.
- Este plan debe mantenerse en `docs/plan-proyecto-soa.md` dentro del repositorio y en el archivo local de planificación.
- Acordar una rama principal protegida de cambios directos y usar ramas para el trabajo individual; integrar después de revisar y probar. No subir contraseñas ni configuración local sensible.

## 4. Alcance funcional

### Usuario
- Iniciar y cerrar sesión.
- Ver qué equipos están disponibles.
- Solicitar el préstamo de un equipo.
- Consultar el estado y el historial de sus propias solicitudes.

### Administrador
- Registrar, listar, editar y desactivar equipos.
- Ver solicitudes pendientes y aprobarlas o rechazarlas.
- Registrar la entrega y la devolución de un equipo.
- Revisar el historial de préstamos y una observación de daño, si se reporta.

### Estados iniciales de una solicitud
`SOLICITADO`, `APROBADO`, `RECHAZADO`, `ENTREGADO`, `DEVUELTO`.

La transición de estados debe estar controlada por el servicio; no permitir, por ejemplo, devolver una solicitud que nunca fue entregada. Una solicitud manejará un equipo para limitar la complejidad.

## 5. Exclusiones y restricciones globales

1. **No incluir facturas, pagos, precios, multas, recargos, carrito de compras ni cobros por atraso.** Los préstamos son gratuitos.
2. No añadir microservicios, mensajería, notificaciones por correo, aplicación móvil, reportes PDF/Excel ni integración con servicios externos.
3. No usar JWT, OAuth, proveedores de identidad ni permisos complejos. Solo login y los roles ADMIN/USUARIO.
4. No permitir que un usuario normal cree cuentas de administrador ni vea solicitudes de otras personas.
5. No poner reglas de negocio dentro del HTML ni consultas a la base de datos en los controladores.
6. No copiar clases enteras de internet/IA sin que el equipo pueda explicar cada anotación, método y dependencia.
7. No cambiar versiones de Java, Spring Boot o dependencias sin aprobación de los cuatro y verificación en IntelliJ.
8. Mantener nombres claros en español y un formato consistente. Cada método debe tener una responsabilidad concreta; no crear capas o patrones que el grupo no pueda justificar.
9. No editar la carpeta de la exposición `grupouno`; sirve solo para consulta.
10. Las contraseñas de prueba no deben ser contraseñas reales de los integrantes ni quedar publicadas en el repositorio.

## 6. Reparto de responsabilidades

Cada integrante se responsabiliza de su módulo de punta a punta: modelo necesario, repositorio, servicio, controlador/rutas, pantallas Bootstrap relacionadas y comprobación manual de sus casos. Se buscará un volumen similar; la dificultad exacta depende de la integración y se revisará en las reuniones.

### Kevin Velasco — Usuarios e inicio de sesión

**Entregables**
- Modelo y persistencia mínimos para cuentas de usuario y su rol.
- Inicio/cierre de sesión y configuración básica de Spring Security.
- Protección de las rutas de administrador y de usuario.
- Vista de login con Bootstrap y creación documentada de las cuentas de demostración.
- Comprobación de que ADMIN puede administrar y USUARIO no puede entrar a esas rutas.

**Restricciones propias**
- Solo dos roles: `ADMIN` y `USUARIO`.
- No implementar registro público, recuperación por correo, bloqueo avanzado, JWT, OAuth ni jerarquías de permisos.
- Usar almacenamiento seguro de contraseñas; nunca guardar contraseñas en texto plano.
- Presentar al equipo la configuración de seguridad antes de conectarla al resto de controladores.

### Manolo Garcia — Catálogo de equipos

**Entregables**
- Modelo, repositorio y servicio de equipos.
- Listado, creación, edición y desactivación de equipos.
- Pantalla administrativa de catálogo con Bootstrap y listado de equipos disponibles para usuarios.
- Comprobaciones manuales de alta, edición, disponibilidad y validaciones básicas.

**Restricciones propias**
- Mantener solo los datos necesarios: identificador, nombre, descripción breve y estado/disponibilidad.
- No implementar compras, proveedores, costos, depreciación, inventario contable ni facturación.
- No borrar físicamente un equipo que tenga historial de préstamos; usar desactivación si el equipo acuerda ese comportamiento.

### Luis Miranda — Solicitudes y aprobación

**Entregables**
- Definir con Emilio el modelo compartido de préstamo y sus estados antes de crear archivos.
- Lógica para que un usuario solicite un equipo disponible y consulte sus propias solicitudes.
- Pantalla/listado de solicitudes pendientes para el administrador.
- Aprobación o rechazo con las validaciones de estado acordadas.
- Comprobaciones manuales de solicitud válida, equipo no disponible, aprobación y rechazo.

**Restricciones propias**
- Una solicitud corresponde a un solo equipo.
- No implementar reservas recurrentes, calendario complejo, listas de espera ni notificaciones.
- No permitir que el usuario cambie por sí mismo el estado de aprobación.

### Emilio Abril — Entrega, devolución e historial

**Entregables**
- Coordinar con Luis el uso del modelo/repositorio compartido; no crear una segunda entidad de préstamo.
- Registrar la entrega de préstamos aprobados y su devolución.
- Registrar una observación descriptiva opcional de devolución/daño, sin valor monetario.
- Pantallas de devolución e historial, respetando quién puede ver cada registro.
- Comprobaciones manuales de entrega, devolución, historial propio y acceso administrativo.

**Restricciones propias**
- No implementar multas, cargos, cálculos de dinero ni sanciones automáticas.
- No registrar una entrega si no está aprobada ni una devolución si no fue entregada.
- No duplicar el flujo de solicitud/aprobación de Luis; acordar nombres de métodos y estados con él antes de programar.

## 7. Reglas para trabajar en grupo

- Acordar primero los nombres de entidades, campos, rutas y estados. Luis y Emilio son responsables de cerrar juntos el contrato del préstamo antes de trabajar en paralelo.
- Cada dueño de módulo conserva la responsabilidad de sus archivos. Si otra persona necesita modificarlos, lo coordina primero con el dueño.
- Integrar cambios pequeños y ejecutar el proyecto después de cada integración; no dejar toda la unión para el final.
- Si utilizan Git, trabajar en ramas individuales y revisar los cambios de otra persona antes de integrarlos. No subir contraseñas, archivos personales ni configuraciones locales.
- Cada integrante debe poder explicar sus clases y demostrar sus casos. En las revisiones, otro compañero probará el módulo para detectar errores de permisos e integración.
- Llevar una lista compartida de tareas con estado: pendiente, en progreso, bloqueada o terminada. Avisar bloqueos con tiempo y redistribuir solo de común acuerdo.

## 8. Etapas sugeridas

1. **Acordar alcance:** confirmar que el tema no se repite en otro grupo y fijar los contratos iniciales de la API REST (rutas, datos JSON, roles y estados).
2. **Diseñar contratos:** dibujar el modelo mínimo, campos, estados y pantallas; acordar estructura de paquetes y rutas.
3. **Preparar la base:** crear la aplicación Spring Boot, revisar dependencias, conexión local a MySQL y estructura de paquetes.
4. **Desarrollar módulos:** avanzar en paralelo con las responsabilidades del apartado 6; integrar frecuentemente.
5. **Integrar seguridad y navegación:** validar login, permisos ADMIN/USUARIO y acceso correcto a las pantallas.
6. **Probar el recorrido completo:** iniciar sesión como usuario, solicitar equipo, iniciar sesión como administrador, aprobar, registrar entrega y devolución, y revisar el historial.
7. **Preparar entrega y exposición:** documentar cómo ejecutar el proyecto, crear la base de datos y usar las cuentas de demostración; cada integrante expone su módulo y las clases que implementó.

## 9. Criterios de finalización

- El proyecto inicia desde IntelliJ/Maven y conecta con MySQL según instrucciones del equipo.
- Existen los dos perfiles requeridos y las rutas están protegidas por rol.
- Se puede completar el recorrido de préstamo desde la solicitud hasta la devolución.
- La disponibilidad del equipo se actualiza de manera coherente.
- Las pantallas son utilizables y están maquetadas con Bootstrap.
- No se incluye facturación, cobros ni funciones fuera del alcance acordado.
- Los cuatro integrantes pueden explicar la estructura `model` → `repository` → `service` → `controller` y las clases nuevas de seguridad.
- La aplicación expone una API REST desde Spring Boot y el cliente Bootstrap la consume; no se crean microservicios independientes.
