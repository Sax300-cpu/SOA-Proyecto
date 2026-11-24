package com.soa.proyecto.dto;

// Cuerpo opcional al aprobar: plazo de devolución en horas (1 a 720). Si no llega se usa el predeterminado.
public record AprobacionPeticion(Integer plazoHoras) {
}
