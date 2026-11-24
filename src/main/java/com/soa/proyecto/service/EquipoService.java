package com.soa.proyecto.service;

import com.soa.proyecto.dto.EquipoRequest;
import com.soa.proyecto.model.Equipo;
import com.soa.proyecto.model.EstadoPrestamo;
import com.soa.proyecto.repository.EquipoRepository;
import com.soa.proyecto.repository.PrestamoRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

// Reglas de negocio del catálogo: listar, crear, editar y desactivar equipos sin borrar historial.
@Service
@RequiredArgsConstructor
public class EquipoService {
    private final EquipoRepository repository;
    private final PrestamoRepository prestamos;

    @Transactional(readOnly = true)
    public List<Equipo> listar(boolean soloDisponibles) {
        return repository.findAll().stream()
                .filter(e -> Boolean.TRUE.equals(e.getActivo()))
                .filter(e -> !soloDisponibles || Boolean.TRUE.equals(e.getDisponible()))
                .toList();
    }

    @Transactional(readOnly = true)
    public Equipo detalle(Long id) {
        return exigirActivo(repository.findById(id).orElseThrow(() -> noEncontrado()));
    }

    @Transactional
    public Equipo crear(EquipoRequest datos) {
        Equipo equipo = new Equipo();
        aplicarDatos(equipo, datos);
        equipo.setActivo(true);
        equipo.setDisponible(true);
        Equipo guardado = repository.save(equipo);
        guardado.setCodigo(generarCodigo(guardado.getId()));
        return repository.save(guardado);
    }

    @Transactional
    public Equipo editar(Long id, EquipoRequest datos) {
        Equipo equipo = exigirActivo(repository.findEquipoById(id).orElseThrow(() -> noEncontrado()));
        aplicarDatos(equipo, datos);
        if (equipo.getCodigo() == null || equipo.getCodigo().isBlank()) {
            equipo.setCodigo(generarCodigo(equipo.getId()));
        }
        return repository.save(equipo);
    }

    @Transactional
    public Equipo desactivar(Long id) {
        Equipo equipo = repository.findEquipoById(id).orElseThrow(() -> noEncontrado());
        if (Boolean.TRUE.equals(equipo.getActivo()) && prestamos.existsByEquipoIdAndEstado(id, EstadoPrestamo.APROBADO)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "No se puede desactivar un equipo con un préstamo aprobado pendiente de entrega");
        }
        equipo.setActivo(false);
        return repository.save(equipo);
    }

    private void aplicarDatos(Equipo equipo, EquipoRequest datos) {
        if (datos == null || datos.nombre() == null || datos.nombre().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "El nombre es obligatorio");
        }
        String nombre = datos.nombre().strip();
        String descripcion = datos.descripcion() == null ? null : datos.descripcion().strip();
        if (nombre.length() > 50 || (descripcion != null && descripcion.length() > 200)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "El nombre admite hasta 50 caracteres y la descripción hasta 200");
        }
        equipo.setNombre(nombre);
        equipo.setDescripcion(descripcion);
    }

    // Genera un código de inventario legible y único a partir del ID autoincremental (ej. EQUIPO-001).
    public static String generarCodigo(Long id) {
        return id == null ? null : String.format("EQUIPO-%03d", id);
    }

    private Equipo exigirActivo(Equipo equipo) {
        if (!Boolean.TRUE.equals(equipo.getActivo())) {
            throw noEncontrado();
        }
        return equipo;
    }

    private ResponseStatusException noEncontrado() {
        return new ResponseStatusException(HttpStatus.NOT_FOUND, "Equipo no encontrado o desactivado");
    }
}
