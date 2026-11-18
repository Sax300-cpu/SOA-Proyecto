package com.soa.proyecto.dto;

// La disponibilidad la controla el préstamo; no se recibe desde el catálogo.
public record EquipoRequest(String nombre, String descripcion) { }
