package com.soa.proyecto.dto;

// Cuerpo opcional de la entrega: estado en que sale el equipo (máx. 300 caracteres) y plazo de devolución
// en horas (1 a 720). Si no llega plazo se usa el del equipo.
public record EntregaPeticion(String observacion, Integer plazoHoras) {

    public EntregaPeticion(String observacion) {
        this(observacion, null);
    }
}
