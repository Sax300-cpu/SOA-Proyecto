package com.soa.proyecto.controller;

import com.soa.proyecto.dto.UsuarioNuevoPeticion;
import com.soa.proyecto.dto.UsuarioResumen;
import com.soa.proyecto.service.UsuarioService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

// Solo ADMIN (regla en SecurityConfig): listar usuarios y bloquear o desbloquear cuentas.
@RestController
@RequestMapping("/api/usuarios")
@RequiredArgsConstructor
public class UsuarioController {

    private final UsuarioService servicio;

    @GetMapping
    public List<UsuarioResumen> listar() {
        return servicio.listar();
    }

    // El administrador crea una cuenta con su contraseña inicial y el rol que elija.
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public UsuarioResumen crear(@RequestBody(required = false) UsuarioNuevoPeticion datos) {
        return datos == null
                ? servicio.crearPorAdmin(null, null, null, null)
                : servicio.crearPorAdmin(datos.nombre(), datos.username(), datos.password(), datos.rol());
    }

    @PutMapping("/{id}/bloquear")
    public UsuarioResumen bloquear(@PathVariable Long id) {
        return servicio.bloquear(id);
    }

    @PutMapping("/{id}/desbloquear")
    public UsuarioResumen desbloquear(@PathVariable Long id) {
        return servicio.desbloquear(id);
    }
}
