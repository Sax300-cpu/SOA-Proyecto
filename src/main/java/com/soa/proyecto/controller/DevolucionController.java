package com.soa.proyecto.controller;

import com.soa.proyecto.dto.DevolucionPeticion;
import com.soa.proyecto.model.EstadoPrestamo;
import com.soa.proyecto.model.Prestamo;
import com.soa.proyecto.service.DevolucionService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

// Endpoints REST para registrar entregas, devoluciones y consultar el historial.
@RestController
@RequiredArgsConstructor
public class DevolucionController {

    private final DevolucionService servicio;

    @PutMapping("/api/prestamos/{id}/entregar")
    public Prestamo entregar(@PathVariable Long id, Authentication sesion) {
        exigirRol(sesion, "ADMIN");
        return servicio.entregar(id);
    }

    @PutMapping("/api/prestamos/{id}/devolver")
    public Prestamo devolver(@PathVariable Long id,
                             @RequestBody(required = false) DevolucionPeticion peticion,
                             Authentication sesion) {
        exigirRol(sesion, "ADMIN");
        return servicio.devolver(id, peticion == null ? null : peticion.observacion());
    }

    @GetMapping("/api/prestamos")
    public List<Prestamo> historial(@RequestParam(required = false) EstadoPrestamo estado,
                                    Authentication sesion) {
        exigirRol(sesion, "ADMIN");
        return servicio.historial(estado);
    }

    // /mios pertenece a Luis; esta ruta permite integrar el historial sin duplicarla.
    @GetMapping("/api/historial/mio")
    public List<Prestamo> historialPropio(@RequestParam(required = false) EstadoPrestamo estado,
                                         Authentication sesion) {
        exigirRol(sesion, "USUARIO");
        return servicio.historialPropio(sesion.getName(), estado);
    }

    private void exigirRol(Authentication sesion, String rol) {
        if (sesion == null || !sesion.isAuthenticated() || sesion instanceof AnonymousAuthenticationToken) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Inicia sesión para continuar");
        }
        if (sesion.getAuthorities().stream().noneMatch(a -> a.getAuthority().equals("ROLE_" + rol))) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "No tienes permiso para esta operación");
        }
    }
}
