package com.soa.proyecto.dto;

// "condicion" es BUEN_ESTADO, CON_DETALLES o DANADO; si no llega se asume BUEN_ESTADO.
public record DevolucionPeticion(String observacion, String condicion) {
}
