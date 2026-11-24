package com.soa.proyecto.controller;

import com.soa.proyecto.dto.RegistroPeticion;
import com.soa.proyecto.dto.UsuarioSesion;
import com.soa.proyecto.model.Rol;
import com.soa.proyecto.model.Usuario;
import com.soa.proyecto.repository.UsuarioRepository;
import com.soa.proyecto.service.UsuarioService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.Map;

// Endpoints públicos de sesión: token CSRF, usuario actual (/me) y registro.
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final UsuarioRepository usuarioRepository;
    private final UsuarioService usuarioService;

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

    // Crea una cuenta nueva asignando siempre rol USUARIO (nunca ADMIN). Las reglas viven en UsuarioService.
    @PostMapping("/registro")
    public ResponseEntity<?> registrar(@RequestBody(required = false) RegistroPeticion datos) {
        try {
            Usuario nuevo = usuarioService.crear(
                    datos == null ? null : datos.nombre(),
                    datos == null ? null : datos.username(),
                    datos == null ? null : datos.password(),
                    Rol.USUARIO);
            return ResponseEntity.status(HttpStatus.CREATED)
                    .body(new UsuarioSesion(nuevo.getUsername(), nuevo.getNombre(), nuevo.getRol()));
        } catch (ResponseStatusException error) {
            return ResponseEntity.status(error.getStatusCode())
                    .body(Map.of("error", error.getReason() == null ? "No se pudo crear la cuenta." : error.getReason()));
        }
    }
}
