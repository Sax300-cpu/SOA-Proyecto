package com.soa.proyecto.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.server.ResponseStatusException;

import java.util.Map;

// Devuelve los errores de usuarios como JSON {"error": "..."} para mostrarlos en pantalla.
@RestControllerAdvice(assignableTypes = UsuarioController.class)
public class UsuarioErrores {
    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<Map<String, String>> negocio(ResponseStatusException error) {
        return ResponseEntity.status(error.getStatusCode())
                .body(Map.of("error", error.getReason() == null ? "Operación no permitida" : error.getReason()));
    }
}
