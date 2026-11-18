package com.soa.proyecto.service;

import com.soa.proyecto.dto.EquipoRequest;
import com.soa.proyecto.model.Equipo;
import com.soa.proyecto.repository.EquipoRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@Service
public class EquipoService {
    private final EquipoRepository repository;

    public EquipoService(EquipoRepository repository) {
        this.repository = repository;
    }

    @Transactional(readOnly = true)
    public List<Equipo> listar(boolean soloDisponibles) {
        return repository.findAll().stream()
                .filter(e -> Boolean.TRUE.equals(e.getActivo()))
                .filter(e -> !soloDisponibles || Boolean.TRUE.equals(e.getDisponible()))
                .toList();
    }

    @Transactional(readOnly = true)
    public Equipo detalle(Long id) {
        return exigirActivo(repository.findById(id).orElseThrow(this::noEncontrado));
    }

    @Transactional
    public Equipo crear(EquipoRequest datos) {
        Equipo equipo = new Equipo();
        aplicarDatos(equipo, datos);
        equipo.setActivo(true);
        equipo.setDisponible(true);
        return repository.save(equipo);
    }

    @Transactional
    public Equipo editar(Long id, EquipoRequest datos) {
        Equipo equipo = exigirActivo(repository.findEquipoById(id).orElseThrow(this::noEncontrado));
        aplicarDatos(equipo, datos);
        return repository.save(equipo);
    }

    @Transactional
    public Equipo desactivar(Long id) {
        Equipo equipo = repository.findEquipoById(id).orElseThrow(this::noEncontrado);
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
