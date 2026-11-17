# Módulo de Emilio — Entrega, devolución e historial

## Alcance e integración

Clases propias: `DevolucionService`, `DevolucionController` y `DevolucionErrores`.
Se reutilizan las entidades y repositorios existentes sin modificarlos. No se implementan
la solicitud, la aprobación, el rechazo, el catálogo ni el login de otros integrantes.

La rama se creó desde `develop`. La interfaz necesita integrar `feature/seguridad` de
Kevin: `/api/auth/me`, `/api/auth/csrf`, login por sesión y serialización JSON mediante
`spring-boot-starter-json`, declarado en esa rama. No se altera su configuración.
Los controladores comprueban también los roles para proteger las nuevas rutas.
El historial propio está fuera de `/api/prestamos/**`, patrón reservado a ADMIN por Kevin.

El servicio valida estados dentro de una transacción, bloquea y relee el préstamo,
y bloquea el equipo antes de actualizarlo. No es necesario añadir métodos a repositorios
ajenos. La devolución conserva `activo`: un equipo desactivado sigue desactivado.
Manolo debe filtrar por `activo` además de `disponible` en el catálogo de disponibles.

## Uso

1. Integrar la seguridad y configurar MySQL según el README.
2. Con JDK 25, ejecutar `.\mvnw.cmd spring-boot:run`.
3. Iniciar sesión con el módulo de Kevin y abrir `http://localhost:8080/devoluciones.html`.
4. ADMIN puede entregar aprobados, devolver entregados y consultar todo el historial.
   USUARIO solo consulta su historial; no puede indicar el id de otra cuenta.

La página usa Bootstrap 5.3.3 desde jsDelivr; sus estilos requieren acceso a ese CDN.
Los nombres de equipo/usuario se presentan como identificadores porque el préstamo
guarda ids y este módulo no depende de endpoints de catálogo todavía pendientes.

## Peticiones

Usar la cookie de sesión y, en escrituras, el encabezado indicado por
`GET /api/auth/csrf` con su token vigente.

```http
PUT /api/prestamos/1/entregar
```

```http
PUT /api/prestamos/1/devolver
Content-Type: application/json

{"observacion":"Pantalla rayada"}
```

También se acepta devolver sin cuerpo, con `{}` o con observación nula/en blanco.
`GET /api/prestamos` consulta el historial ADMIN; `GET /api/historial/mio` consulta
el propio. Ambos aceptan `?estado=DEVUELTO` y los otros estados del enum existente.

## Comprobación manual con otro integrante

Preparar dos usuarios, un administrador, un equipo activo y préstamos de prueba
en distintos estados, mediante el módulo de Luis cuando esté integrado o datos locales
de demostración. No se añaden endpoints para manipular estados de prueba.

| Caso | Resultado esperado |
|---|---|
| ADMIN entrega APROBADO | ENTREGADO, fecha de entrega y equipo no disponible |
| Entregar SOLICITADO, RECHAZADO, ENTREGADO o DEVUELTO | 400, sin cambios |
| Entregar equipo desactivado | 400, sin cambios |
| ADMIN devuelve ENTREGADO con observación | DEVUELTO, fecha, observación y equipo disponible |
| Devolver sin observación | Éxito, observación nula |
| Observación de 300 / 301 caracteres | Éxito / 400 |
| Devolver antes de entregar o por segunda vez | 400, sin cambios |
| Equipo desactivado durante el préstamo | Al devolver sigue desactivado |
| Préstamo o equipo inexistente | 404, sin cambios |
| ADMIN consulta y filtra historial | Todos los registros o solo el estado elegido |
| USUARIO consulta historial propio | Solo registros de su cuenta, incluso con parámetros de otro usuario |
| USUARIO intenta entrega, devolución o historial ADMIN | 403 |
| Sin sesión / escritura sin CSRF válido | 401 o rechazo del filtro / 403 |
| Dos administradores entregan/devuelven simultáneamente | Una operación válida; la repetida se rechaza |
| Tabla vacía, error de red o sesión vencida | Aviso visible y opción de reintentar |
| Observación con HTML | Se muestra como texto, sin ejecutarse |
| Cancelar diálogo, navegar por teclado y pantalla móvil | Sin escritura al cancelar, foco visible y tabla desplazable |

## Pruebas automatizadas

```powershell
.\mvnw.cmd -DskipTests compile
.\mvnw.cmd '-Dtest=DevolucionServiceTest,DevolucionControllerTest' test
```

Las pruebas aisladas de servicio/controlador no necesitan MySQL. Cubren transiciones,
fechas, disponibilidad, límites de observación, registros inexistentes, filtro/orden y
restricciones por rol/sesión. No sustituyen las pruebas reales de transacciones, bloqueos,
CSRF y permisos de Spring Security con MySQL y el módulo de Kevin integrado.

El test original `ProyectoSoaApplicationTests` carga el contexto completo y requiere
la configuración de base de datos del proyecto; no se modifica para ocultar esa dependencia.

## Validación realizada (16 de noviembre de 2026)

- Compilación Maven con JDK 25: correcta. Se usó un JDK portable temporal porque el
  Java activo de la máquina era 21 y `JAVA_HOME` apuntaba a una ubicación inválida.
  No se cambió la versión del proyecto ni la configuración permanente del equipo.
- `DevolucionServiceTest` y `DevolucionControllerTest`: 21 pruebas, sin fallos ni errores.
- Comprobación en Chrome sin ventana con respuestas de API simuladas: 16 verificaciones
  correctas, incluyendo entrega/devolución, observación y CSRF enviados, cancelación,
  conservación del formulario tras error, contenido HTML mostrado como texto, estado
  vacío, separación de vistas por rol, sesión vencida y ancho móvil sin desbordar la página.
- Capturas y script auxiliar de esa comprobación local en `target/` (ignorado por Git).
- Sintaxis JavaScript comprobada con `node --check`.
- Pendientes: arranque con MySQL, filtros reales de Spring Security/CSRF, bloqueos
  simultáneos y revisión manual por otro integrante después de integrar los módulos.
- Postman CLI está instalado; `postman status` informó proyecto sin inicializar y
  fallo de consulta de su repositorio de skills (`ENOTFOUND`). No se publicaron recursos
  ni se atribuyen estas pruebas a una ejecución de Postman.
