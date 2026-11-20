package com.soa.proyecto.controller;

import com.soa.proyecto.service.EquipoNoDisponibleException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.server.ResponseStatusException;

import java.util.Map;

@RestControllerAdvice(assignableTypes = {DevolucionController.class, SolicitudController.class})
public class DevolucionErrores {

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<Map<String, String>> negocio(ResponseStatusException error) {
        return ResponseEntity.status(error.getStatusCode())
                .body(Map.of("error", error.getReason() == null ? "Operación no permitida" : error.getReason()));
    }

    @ExceptionHandler(EquipoNoDisponibleException.class)
    public ResponseEntity<Map<String, String>> equipoNoDisponible(EquipoNoDisponibleException error) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(Map.of("error", error.getMessage()));
    }

    @ExceptionHandler({MethodArgumentTypeMismatchException.class, HttpMessageNotReadableException.class})
    public ResponseEntity<Map<String, String>> datosInvalidos(Exception error) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(Map.of("error", "Revisa el identificador, el estado y el cuerpo de la petición"));
    }
}
