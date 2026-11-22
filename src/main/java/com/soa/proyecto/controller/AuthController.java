package com.soa.proyecto.controller;

import com.soa.proyecto.dto.RegistroPeticion;
import com.soa.proyecto.dto.UsuarioSesion;
import com.soa.proyecto.model.Rol;
import com.soa.proyecto.model.Usuario;
import com.soa.proyecto.repository.UsuarioRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;
import java.util.Map;

// Endpoints públicos de sesión: token CSRF, usuario actual (/me) y registro.
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final UsuarioRepository usuarioRepository;
    private final PasswordEncoder passwordEncoder;

    @GetMapping("/csrf")
    public CsrfToken csrf(CsrfToken csrfToken) {
        return csrfToken;
    }

    @GetMapping("/me")
    public ResponseEntity<UsuarioSesion> me(Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()
                || authentication instanceof AnonymousAuthenticationToken) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        Usuario usuario = usuarioRepository.findByUsername(authentication.getName()).orElse(null);
        if (usuario == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        return ResponseEntity.ok(new UsuarioSesion(usuario.getUsername(), usuario.getNombre(), usuario.getRol()));
    }

    // Crea una cuenta nueva asignando siempre rol USUARIO (nunca ADMIN).
    @PostMapping("/registro")
    public ResponseEntity<?> registrar(@RequestBody(required = false) RegistroPeticion datos) {
        if (datos == null || datos.nombre() == null || datos.nombre().isBlank()
                || datos.username() == null || datos.username().isBlank()
                || datos.password() == null || datos.password().isBlank()) {
            return ResponseEntity.badRequest()
                    .body(Map.of("error", "Todos los campos son obligatorios."));
        }

        String nombre = datos.nombre().strip();
        String username = datos.username().strip();
        String password = datos.password();

        if (nombre.length() > 50) {
            return ResponseEntity.badRequest()
                    .body(Map.of("error", "El nombre no puede superar los 50 caracteres."));
        }
        if (username.length() > 30 || username.contains(" ")) {
            return ResponseEntity.badRequest()
                    .body(Map.of("error", "El usuario no debe tener espacios y admite hasta 30 caracteres."));
        }
        if (password.length() < 8) {
            return ResponseEntity.badRequest()
                    .body(Map.of("error", "La contraseña debe tener al menos 8 caracteres."));
        }
        if (password.getBytes(StandardCharsets.UTF_8).length > 72) {
            return ResponseEntity.badRequest()
                    .body(Map.of("error", "La contraseña no puede superar 72 bytes en UTF-8."));
        }
        if (usuarioRepository.existsByUsername(username)) {
            return usuarioDuplicado();
        }

        Usuario nuevo = new Usuario();
        nuevo.setNombre(nombre);
        nuevo.setUsername(username);
        nuevo.setPassword(passwordEncoder.encode(password));
        nuevo.setRol(Rol.USUARIO);
        try {
            usuarioRepository.save(nuevo);
        } catch (DataIntegrityViolationException error) {
            // Otra solicitud pudo crear la cuenta después de la comprobación inicial.
            if (usuarioRepository.existsByUsername(username)) {
                return usuarioDuplicado();
            }
            throw error;
        }

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(new UsuarioSesion(nuevo.getUsername(), nuevo.getNombre(), nuevo.getRol()));
    }

    private ResponseEntity<Map<String, String>> usuarioDuplicado() {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(Map.of("error", "Ese nombre de usuario ya está registrado."));
    }
}
