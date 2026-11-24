package com.soa.proyecto.dto;

// Datos para que el administrador cree una cuenta. "rol" es USUARIO (por defecto) o ADMIN.
public record UsuarioNuevoPeticion(String nombre, String username, String password, String rol) {
}
