package com.soa.proyecto.controller;

import com.soa.proyecto.dto.UsuarioSesion;
import com.soa.proyecto.model.Rol;
import com.soa.proyecto.model.Usuario;
import com.soa.proyecto.repository.UsuarioRepository;
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

import java.util.Map;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final UsuarioRepository usuarioRepository;
    private final PasswordEncoder passwordEncoder;

    public AuthController(UsuarioRepository usuarioRepository, PasswordEncoder passwordEncoder) {
        this.usuarioRepository = usuarioRepository;
        this.passwordEncoder = passwordEncoder;
    }

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
        if (password.length() < 6 || password.length() > 72) {
            return ResponseEntity.badRequest()
                    .body(Map.of("error", "La contraseña debe tener entre 6 y 72 caracteres."));
        }
        if (usuarioRepository.existsByUsername(username)) {
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(Map.of("error", "Ese nombre de usuario ya está registrado."));
        }

        Usuario nuevo = new Usuario();
        nuevo.setNombre(nombre);
        nuevo.setUsername(username);
        nuevo.setPassword(passwordEncoder.encode(password));
        nuevo.setRol(Rol.USUARIO);
        usuarioRepository.save(nuevo);

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(new UsuarioSesion(nuevo.getUsername(), nuevo.getNombre(), nuevo.getRol()));
    }

    public record RegistroPeticion(String nombre, String username, String password) {
    }
}
