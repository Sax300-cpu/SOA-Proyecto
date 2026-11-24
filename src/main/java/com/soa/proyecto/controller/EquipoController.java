package com.soa.proyecto.controller;

import com.soa.proyecto.dto.EquipoRequest;
import com.soa.proyecto.model.Equipo;
import com.soa.proyecto.service.EquipoService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;

// Los permisos ADMIN/USUARIO y CSRF se aplican en SecurityConfig.
@RestController
@RequestMapping("/api/equipos")
@RequiredArgsConstructor
public class EquipoController {
    private final EquipoService servicio;

    @GetMapping
    public List<Equipo> listar(@RequestParam(defaultValue = "false") boolean disponibles) {
        return servicio.listar(disponibles);
    }

    // Solo ADMIN (regla en SecurityConfig).
    @GetMapping("/desactivados")
    public List<Equipo> desactivados() {
        return servicio.listarDesactivados();
    }

    @GetMapping("/{id}")
    public Equipo detalle(@PathVariable Long id) {
        return servicio.detalle(id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public Equipo crear(@RequestBody EquipoRequest datos) {
        return servicio.crear(datos);
    }

    @PutMapping("/{id}")
    public Equipo editar(@PathVariable Long id, @RequestBody EquipoRequest datos) {
        return servicio.editar(id, datos);
    }

    @PutMapping("/{id}/desactivar")
    public Equipo desactivar(@PathVariable Long id) {
        return servicio.desactivar(id);
    }

    @PutMapping("/{id}/activar")
    public Equipo activar(@PathVariable Long id) {
        return servicio.activar(id);
    }
}
