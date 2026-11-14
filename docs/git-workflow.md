# Flujo de trabajo con Git

Reglas y pasos para que los cuatro integrantes trabajen en paralelo sin pisarse. **Todo avance entra a `develop` mediante pull request; nadie sube directo a `main`.**

## 1. Ramas del proyecto

| Rama | Uso | ¿Quién sube directo? |
|---|---|---|
| `main` | Versión estable, para entregas | Nadie (protegida) |
| `develop` | Integración de todos los avances | Solo mediante pull request |
| `feature/<modulo>` | Trabajo de cada integrante | El dueño de la rama |

## 2. Configuración inicial (una sola vez por persona)

```bash
git clone https://github.com/Sax300-cpu/SOA-Proyecto.git
cd SOA-Proyecto
git checkout develop
git pull origin develop
```

## 3. Empezar una tarea (cada vez)

```bash
git checkout develop
git pull origin develop
git checkout -b feature/mi-modulo
```

Ejemplos de nombre de rama:

- `feature/seguridad` (Kevin)
- `feature/equipos` (Manolo)
- `feature/prestamos` (Luis)
- `feature/devolucion` (Emilio)

## 4. Hacer commits pequeños y claros

```bash
git status
git add .
git commit -m "feat: descripción corta de lo que hice"
```

Prefijos sugeridos para el mensaje:

- `feat:` nueva funcionalidad
- `fix:` corrección de un error
- `docs:` cambios en documentación
- `refactor:` mejora sin cambiar el comportamiento

## 5. Subir la rama al repositorio

Primera vez:

```bash
git push -u origin feature/mi-modulo
```

Las siguientes veces:

```bash
git push
```

## 6. Mantener la rama actualizada con develop

Si otro compañero ya integró cambios en `develop`, antes de seguir o de abrir el pull request:

```bash
git checkout develop
git pull origin develop
git checkout feature/mi-modulo
git merge develop
```

Si hay conflictos, Git los marca. Resolverlos hablando con el dueño del archivo y luego:

```bash
git add .
git commit -m "merge: integrar develop"
```

## 7. Abrir un pull request hacia develop

1. En GitHub, entrar a la rama `feature/mi-modulo`.
2. Clic en **Compare & pull request**.
3. Base: `develop`. Compare: `feature/mi-modulo`.
4. Escribir una descripción corta de lo que hace.
5. Asignar a un compañero como revisor.

**Regla:** no fusionar el propio pull request sin que otro integrante lo revise.

## 8. Revisar el pull request de un compañero

1. Leer los cambios y verificar que cumple su módulo y las reglas.
2. Probar la rama localmente si hace falta.
3. Dejar comentarios si hay algo por corregir.
4. Aprobar cuando esté correcto.

## 9. Después de fusionar

```bash
git checkout develop
git pull origin develop
git branch -d feature/mi-modulo
```

## 10. Reglas que no se pueden romper

- **Nunca** hacer `git push` directo a `main` ni a `develop`.
- **Nunca** subir contraseñas reales, credenciales de MySQL ni archivos personales.
- **Nunca** subir `target/` ni `.idea/` (ya están en `.gitignore`).
- Antes de abrir un pull request, compilar: `.\mvnw.cmd -DskipTests compile`.
- Cada avance entra por **pull request** a `develop`.

## 11. Usar IntelliJ en lugar de la terminal

El mismo flujo se hace desde IntelliJ:

- `VCS` → `Git` → `Branches...` para crear o cambiar de rama.
- `Commit...` para guardar cambios.
- `Git` → `Pull` / `Push` para traer o subir.
- Al terminar, abrir el pull request desde GitHub.

## 12. Proteger main (una sola vez, el administrador del repositorio)

En GitHub:

1. `Settings` → `Branches` → `Add branch protection rule`.
2. En *Branch name pattern* escribir `main`.
3. Activar `Require a pull request before merging`.
4. Guardar.

Así nadie puede subir directo a `main`, ni siquiera por error.
