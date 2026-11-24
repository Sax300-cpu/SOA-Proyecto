package com.soa.proyecto.dto;

// La disponibilidad la controla el préstamo. El código lo arma el servidor: "prefijo" (3 letras, opcional)
// + número correlativo. Si no llega prefijo, se toma de las 3 primeras letras del nombre. Al editar se ignora.
public record EquipoRequest(String nombre, String descripcion, String prefijo) { }
