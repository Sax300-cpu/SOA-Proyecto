package com.soa.proyecto.dto;

import com.soa.proyecto.model.Rol;
import com.soa.proyecto.model.Usuario;

// Datos de un usuario para el administrador. Nunca incluye la contraseña.
public record UsuarioResumen(Long id, String nombre, String username, Rol rol, boolean bloqueado) {

    public static UsuarioResumen de(Usuario usuario) {
        return new UsuarioResumen(usuario.getId(), usuario.getNombre(), usuario.getUsername(),
                usuario.getRol(), Boolean.TRUE.equals(usuario.getBloqueado()));
    }
}
