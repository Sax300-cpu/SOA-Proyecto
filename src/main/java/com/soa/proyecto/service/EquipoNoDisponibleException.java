package com.soa.proyecto.service;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(HttpStatus.CONFLICT)
public class EquipoNoDisponibleException extends RuntimeException {

    public EquipoNoDisponibleException() {
        super("El equipo no existe o no está disponible");
    }
}
