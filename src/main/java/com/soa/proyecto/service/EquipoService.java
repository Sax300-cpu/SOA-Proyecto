package com.soa.proyecto.service;

import com.soa.proyecto.dto.EquipoRequest;
import com.soa.proyecto.model.CondicionEquipo;
import com.soa.proyecto.model.Equipo;
import com.soa.proyecto.model.EstadoPrestamo;
import com.soa.proyecto.repository.EquipoRepository;
import com.soa.proyecto.repository.PrestamoRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.text.Normalizer;
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

    // Solo para el ADMIN: equipos desactivados, para poder activarlos de nuevo.
    @Transactional(readOnly = true)
    public List<Equipo> listarDesactivados() {
        return repository.findAll().stream()
                .filter(e -> !Boolean.TRUE.equals(e.getActivo()))
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
        equipo.setCodigo(siguienteCodigo(elegirPrefijo(datos)));
        equipo.setActivo(true);
        equipo.setDisponible(true);
        try {
            return repository.save(equipo);
        } catch (DataIntegrityViolationException error) {
            // Dos altas simultáneas con el mismo prefijo calcularon el mismo número.
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Otro equipo tomó ese código al mismo tiempo. Inténtalo de nuevo");
        }
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

    // Vuelve a activar un equipo desactivado. Es repetible (si ya está activo no cambia nada).
    // Queda disponible salvo que esté reservado/en manos de un usuario, tenga un daño sin resolver o esté en mantenimiento.
    @Transactional
    public Equipo activar(Long id) {
        Equipo equipo = repository.findEquipoById(id).orElseThrow(() -> noEncontrado());
        if (Boolean.TRUE.equals(equipo.getActivo())) {
            return equipo;
        }
        boolean enUso = prestamos.existsByEquipoIdAndEstadoIn(id,
                List.of(EstadoPrestamo.APROBADO, EstadoPrestamo.ENTREGADO));
        boolean danoSinResolver = prestamos.existsByEquipoIdAndCondicionDevolucionAndIncidenciaAtendida(
                id, CondicionEquipo.DANADO, false);
        boolean enMantenimiento = prestamos.existsByEquipoIdAndEnMantenimientoTrue(id);
        equipo.setActivo(true);
        equipo.setDisponible(!enUso && !danoSinResolver && !enMantenimiento);
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

    // Prefijo enviado por el cliente (3 letras) o, si no llega, las 3 primeras letras del nombre.
    private String elegirPrefijo(EquipoRequest datos) {
        if (datos.prefijo() == null || datos.prefijo().isBlank()) {
            return prefijoDesdeNombre(datos.nombre());
        }
        String prefijo = datos.prefijo().strip().toUpperCase();
        if (!prefijo.matches("[A-Z]{3}")) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "El prefijo debe tener exactamente 3 letras (A-Z), por ejemplo CAB");
        }
        return prefijo;
    }

    // Quita tildes y símbolos y toma las 3 primeras letras ("Cámara web" -> CAM). Con menos de 3 letras: EQP.
    public static String prefijoDesdeNombre(String nombre) {
        String letras = Normalizer.normalize(nombre == null ? "" : nombre, Normalizer.Form.NFD)
                .replaceAll("[^A-Za-z]", "")
                .toUpperCase();
        return letras.length() >= 3 ? letras.substring(0, 3) : "EQP";
    }

    // Siguiente código del prefijo: si ya existe CAB-009, devuelve CAB-010 (los desactivados también cuentan).
    private String siguienteCodigo(String prefijo) {
        String inicio = prefijo + "-";
        int mayor = 0;
        for (Equipo existente : repository.findByCodigoStartingWith(inicio)) {
            String numero = existente.getCodigo().substring(inicio.length());
            if (numero.matches("\\d{1,6}")) {
                mayor = Math.max(mayor, Integer.parseInt(numero));
            }
        }
        return String.format("%s-%03d", prefijo, mayor + 1);
    }

    // Código de respaldo para equipos antiguos sin código, a partir de su ID (ej. EQUIPO-001).
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
