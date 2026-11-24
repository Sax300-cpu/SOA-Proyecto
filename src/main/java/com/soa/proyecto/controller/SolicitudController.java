package com.soa.proyecto.controller;

import com.soa.proyecto.dto.AnulacionPeticion;
import com.soa.proyecto.dto.AprobacionPeticion;
import com.soa.proyecto.dto.SolicitudPrestamoRequest;
import com.soa.proyecto.model.EstadoPrestamo;
import com.soa.proyecto.model.Prestamo;
import com.soa.proyecto.service.PrestamoService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

// Endpoints REST para solicitar préstamos, ver pendientes y aprobar/rechazar.
@RestController
@RequestMapping("/api/prestamos")
@RequiredArgsConstructor
public class SolicitudController {

    private final PrestamoService prestamoService;

    @PostMapping
    public ResponseEntity<Prestamo> solicitar(
            @RequestBody SolicitudPrestamoRequest solicitud,
            Authentication authentication) {
        if (solicitud == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "El cuerpo de la solicitud es obligatorio");
        }
        Prestamo prestamo = prestamoService.crearSolicitud(authentication.getName(), solicitud.equipoId());
        return ResponseEntity.status(HttpStatus.CREATED).body(prestamo);
    }

    @GetMapping("/mios")
    public List<Prestamo> misSolicitudes(Authentication authentication) {
        return prestamoService.historialDe(authentication.getName());
    }

    @GetMapping("/pendientes")
    public List<Prestamo> pendientes() {
        return prestamoService.buscarPorEstado(EstadoPrestamo.SOLICITADO);
    }

    // Cuerpo opcional {"plazoHoras": 2}: plazo de devolución que fija el administrador.
    @PutMapping("/{id}/aprobar")
    public Prestamo aprobar(@PathVariable Long id, @RequestBody(required = false) AprobacionPeticion peticion) {
        return prestamoService.aprobar(id, peticion == null ? null : peticion.plazoHoras());
    }

    @PutMapping("/{id}/rechazar")
    public Prestamo rechazar(@PathVariable Long id) {
        return prestamoService.rechazar(id);
    }

    // Anula un préstamo aprobado que aún no se entregó (motivo opcional).
    @PutMapping("/{id}/anular")
    public Prestamo anular(@PathVariable Long id, @RequestBody(required = false) AnulacionPeticion peticion) {
        return prestamoService.anular(id, peticion == null ? null : peticion.motivo());
    }
}
