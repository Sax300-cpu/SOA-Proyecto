# SOA - Préstamo de equipos tecnológicos

Sistema web para administrar el préstamo y la devolución de equipos (laptops, proyectores, etc.), sin facturación. Tiene dos perfiles: **ADMIN** y **USUARIO**.

## Tecnologías

- **Cliente:** HTML, CSS, Bootstrap y JavaScript (carpeta `src/main/resources/static`).
- **Servidor:** una sola aplicación Spring Boot con API REST.
- **Persistencia:** Spring Data JPA y MySQL.
- **Seguridad:** Spring Security con inicio de sesión y roles.

## Requisitos previos

- JDK 25
- IntelliJ IDEA (con Maven) o Maven instalado
- MySQL corriendo (por ejemplo, con XAMPP)

## Cómo ejecutar

### Con IntelliJ
1. Abrir el proyecto (`pom.xml`).
2. Esperar a que Maven descargue las dependencias.
3. Ejecutar la clase `ProyectoSoaApplication`.

### Con línea de comandos (Windows)
```bash
.\mvnw.cmd spring-boot:run
```

El proyecto incluye Maven Wrapper, así no hace falta instalar Maven por separado.

La aplicación queda disponible en `http://localhost:8080`.

## Base de datos

La configuración está en `src/main/resources/application.properties`. Por defecto usa la base `soa_prestamos`, que se crea automáticamente si no existe, y las tablas se generan con `ddl-auto=update`.

Ajusta `spring.datasource.username` y `spring.datasource.password` si tu MySQL usa otras credenciales.

## Estructura de paquetes

```
src/main/java/com/soa/proyecto/
├── model/        Entidades (Usuario, Equipo, Prestamo) y enums
├── repository/   Interfaces de acceso a datos (JpaRepository)
├── service/      Lógica de negocio (cada integrante crea sus clases aquí)
├── controller/   Controladores REST (cada integrante crea sus clases aquí)
└── security/     Configuración de Spring Security (Kevin)
```

## Documentos de referencia

- [Plan del proyecto](docs/plan-proyecto-soa.md)
- [Contratos de la API y entidades](docs/contratos-api.md)
- [Sprints y flujo de trabajo](docs/sprints.md)

## Trabajo en equipo

- Trabajar en ramas `feature/<modulo>` y abrir *pull requests* hacia `develop`. No subir directo a `main`.
- No subir contraseñas ni configuración local sensible.
- Antes de modificar un archivo de otro integrante, coordinar con su dueño.
- Ejecutar el proyecto después de cada integración.
