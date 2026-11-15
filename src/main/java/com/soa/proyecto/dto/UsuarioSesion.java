package com.soa.proyecto.dto;

import com.soa.proyecto.model.Rol;
import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class UsuarioSesion {

    private String username;
    private String nombre;
    private Rol rol;
}
