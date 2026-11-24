package com.soa.proyecto.service;

import com.soa.proyecto.dto.UsuarioResumen;
import com.soa.proyecto.model.Rol;
import com.soa.proyecto.model.Usuario;
import com.soa.proyecto.repository.UsuarioRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.nio.charset.StandardCharsets;
import java.util.Comparator;
import java.util.List;

// Gestión de usuarios para el administrador: listar y bloquear/desbloquear cuentas.
@Service
@RequiredArgsConstructor
public class UsuarioService {

    private final UsuarioRepository repository;
    private final PasswordEncoder passwordEncoder;

    @Transactional(readOnly = true)
    public List<UsuarioResumen> listar() {
        return repository.findAll().stream()
                .sorted(Comparator.comparing(Usuario::getNombre, String.CASE_INSENSITIVE_ORDER))
                .map(UsuarioResumen::de)
                .toList();
    }

    // Alta hecha por el administrador: el rol es USUARIO si no se indica.
    public UsuarioResumen crearPorAdmin(String nombre, String username, String password, String rol) {
        Rol rolElegido = Rol.USUARIO;
        if (rol != null && !rol.isBlank()) {
            try {
                rolElegido = Rol.valueOf(rol.strip().toUpperCase());
            } catch (IllegalArgumentException error) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "El rol debe ser USUARIO o ADMIN.");
            }
        }
        return UsuarioResumen.de(crear(nombre, username, password, rolElegido));
    }

    // Crea una cuenta con las reglas del registro público (también las usa /api/auth/registro).
    // La contraseña se guarda cifrada con BCrypt.
    public Usuario crear(String nombreTexto, String usernameTexto, String password, Rol rol) {
        if (nombreTexto == null || nombreTexto.isBlank() || usernameTexto == null || usernameTexto.isBlank()
                || password == null || password.isBlank()) {
            throw datosInvalidos("Todos los campos son obligatorios.");
        }
        String nombre = nombreTexto.strip();
        String username = usernameTexto.strip();
        if (nombre.length() > 50) {
            throw datosInvalidos("El nombre no puede superar los 50 caracteres.");
        }
        if (username.length() > 30 || username.contains(" ")) {
            throw datosInvalidos("El usuario no debe tener espacios y admite hasta 30 caracteres.");
        }
        if (password.length() < 8) {
            throw datosInvalidos("La contraseña debe tener al menos 8 caracteres.");
        }
        if (password.getBytes(StandardCharsets.UTF_8).length > 72) {
            throw datosInvalidos("La contraseña no puede superar 72 bytes en UTF-8.");
        }
        if (repository.existsByUsername(username)) {
            throw usuarioDuplicado();
        }

        Usuario nuevo = new Usuario();
        nuevo.setNombre(nombre);
        nuevo.setUsername(username);
        nuevo.setPassword(passwordEncoder.encode(password));
        nuevo.setRol(rol);
        nuevo.setBloqueado(false);
        try {
            return repository.save(nuevo);
        } catch (DataIntegrityViolationException error) {
            // Otra solicitud pudo crear la cuenta después de la comprobación inicial.
            if (repository.existsByUsername(username)) {
                throw usuarioDuplicado();
            }
            throw error;
        }
    }

    private ResponseStatusException datosInvalidos(String mensaje) {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, mensaje);
    }

    private ResponseStatusException usuarioDuplicado() {
        return new ResponseStatusException(HttpStatus.CONFLICT, "Ese nombre de usuario ya está registrado.");
    }

    @Transactional
    public UsuarioResumen bloquear(Long id) {
        return cambiarBloqueo(id, true);
    }

    @Transactional
    public UsuarioResumen desbloquear(Long id) {
        return cambiarBloqueo(id, false);
    }

    // Repetible: bloquear a quien ya está bloqueado no cambia nada. Las cuentas ADMIN no se bloquean.
    private UsuarioResumen cambiarBloqueo(Long id, boolean bloqueado) {
        Usuario usuario = repository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Usuario no encontrado"));
        if (bloqueado && usuario.getRol() == Rol.ADMIN) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "No se puede bloquear una cuenta de administrador");
        }
        usuario.setBloqueado(bloqueado);
        return UsuarioResumen.de(repository.save(usuario));
    }
}
