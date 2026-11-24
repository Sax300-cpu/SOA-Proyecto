package com.soa.proyecto.service;

import com.soa.proyecto.model.CondicionEquipo;
import com.soa.proyecto.model.Equipo;
import com.soa.proyecto.model.EstadoPrestamo;
import com.soa.proyecto.model.Prestamo;
import com.soa.proyecto.repository.EquipoRepository;
import com.soa.proyecto.repository.PrestamoRepository;
import com.soa.proyecto.repository.UsuarioRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.List;

// Reglas de negocio de entrega, devolución (libera el equipo) e historial por rol.
@Service
@RequiredArgsConstructor
public class DevolucionService {

    private final PrestamoRepository prestamos;
    private final EquipoRepository equipos;
    private final UsuarioRepository usuarios;
    private final EntityManager entityManager;

    private static final List<EstadoPrestamo> ESTADOS_EN_USO = List.of(
            EstadoPrestamo.APROBADO, EstadoPrestamo.ENTREGADO);

    @Value("${prestamos.dias-plazo:3}")
    private int diasPlazo = 3;

    @Transactional
    public Prestamo entregar(Long id) {
        return entregar(id, null);
    }

    @Transactional
    public Prestamo entregar(Long id, String observacion) {
        return entregar(id, observacion, null);
    }

    // La observación de entrega es opcional: cómo sale el equipo (ej. "Con un rayón en la tapa").
    // El plazo de devolución (en horas) es el enviado, o el fijado al aprobar, o el predeterminado de la configuración.
    @Transactional
    public Prestamo entregar(Long id, String observacion, Integer plazoHoras) {
        String texto = limpiarObservacion(observacion);
        if (plazoHoras != null) {
            PrestamoService.validarPlazoHoras(plazoHoras);
        }
        Prestamo prestamo = buscarParaActualizar(id);
        exigirEstado(prestamo, EstadoPrestamo.APROBADO, "Solo se puede entregar un préstamo aprobado");
        Equipo equipo = buscarEquipo(prestamo.getEquipoId());
        if (!Boolean.TRUE.equals(equipo.getActivo())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "El equipo está desactivado");
        }
        LocalDateTime ahora = LocalDateTime.now();
        equipo.setDisponible(false);
        prestamo.setEstado(EstadoPrestamo.ENTREGADO);
        prestamo.setFechaEntrega(ahora);
        int horas = plazoHoras != null ? plazoHoras
                : prestamo.getPlazoHoras() != null ? prestamo.getPlazoHoras() : diasPlazo * 24;
        prestamo.setPlazoHoras(horas);
        prestamo.setFechaLimite(ahora.plusHours(horas));
        prestamo.setObservacionEntrega(texto);
        equipos.save(equipo);
        return prestamos.save(prestamo);
    }

    @Transactional
    public Prestamo devolver(Long id, String observacion) {
        return devolver(id, observacion, null);
    }

    // Si el equipo vuelve con detalles o dañado, la observación es obligatoria y se abre una incidencia.
    // Un equipo DANADO no vuelve al catálogo hasta que el administrador resuelva la incidencia.
    @Transactional
    public Prestamo devolver(Long id, String observacion, String condicionTexto) {
        String texto = limpiarObservacion(observacion);
        CondicionEquipo condicion = interpretarCondicion(condicionTexto);
        if (condicion != CondicionEquipo.BUEN_ESTADO && texto == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Describe el problema en la observación cuando el equipo vuelve con detalles o dañado");
        }
        Prestamo prestamo = buscarParaActualizar(id);
        exigirEstado(prestamo, EstadoPrestamo.ENTREGADO, "Solo se puede devolver un préstamo entregado");
        Equipo equipo = buscarEquipo(prestamo.getEquipoId());
        prestamo.setEstado(EstadoPrestamo.DEVUELTO);
        prestamo.setFechaDevolucion(LocalDateTime.now());
        prestamo.setObservacion(texto);
        prestamo.setCondicionDevolucion(condicion);
        prestamo.setIncidenciaAtendida(condicion == CondicionEquipo.BUEN_ESTADO ? null : false);
        // La devolución no reactiva equipos desactivados por el administrador ni libera equipos con otro bloqueo activo.
        equipo.setDisponible(condicion != CondicionEquipo.DANADO
                && !tieneBloqueoEnOtroPrestamo(equipo.getId(), prestamo.getId()));
        equipos.save(equipo);
        return prestamos.save(prestamo);
    }

    // Resuelve una incidencia abierta. Acciones permitidas:
    //  - CON_DETALLES: ATENDIDA (el equipo sigue disponible), MANTENIMIENTO o BAJA.
    //  - DANADO: REPARADO (vuelve al catálogo), MANTENIMIENTO o BAJA.
    //  - Ya en mantenimiento: REPARADO o BAJA. MANTENIMIENTO deja la incidencia abierta y el equipo fuera del catálogo.
    @Transactional
    public Prestamo atenderIncidencia(Long id, String accionTexto) {
        Prestamo prestamo = buscarParaActualizar(id);
        CondicionEquipo condicion = prestamo.getCondicionDevolucion();
        boolean abierta = prestamo.getEstado() == EstadoPrestamo.DEVUELTO
                && condicion != null && condicion != CondicionEquipo.BUEN_ESTADO
                && !Boolean.TRUE.equals(prestamo.getIncidenciaAtendida());
        if (!abierta) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "El préstamo no tiene una incidencia abierta");
        }
        boolean enMantenimiento = Boolean.TRUE.equals(prestamo.getEnMantenimiento());
        String accion = accionTexto == null ? "" : accionTexto.strip().toUpperCase();
        List<String> permitidas = enMantenimiento ? List.of("REPARADO", "BAJA")
                : condicion == CondicionEquipo.DANADO ? List.of("REPARADO", "MANTENIMIENTO", "BAJA")
                : List.of("ATENDIDA", "MANTENIMIENTO", "BAJA");
        if (!permitidas.contains(accion)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "La acción debe ser una de: " + String.join(", ", permitidas));
        }
        Equipo equipo = buscarEquipo(prestamo.getEquipoId());
        switch (accion) {
            case "ATENDIDA" -> {
                // El equipo ya volvió al catálogo; solo se cierra el aviso.
            }
            case "REPARADO" -> {
                boolean libre = Boolean.TRUE.equals(equipo.getActivo())
                        && !tieneBloqueoEnOtroPrestamo(equipo.getId(), prestamo.getId());
                equipo.setDisponible(libre);
                equipos.save(equipo);
            }
            case "BAJA" -> {
                if (prestamos.existsByEquipoIdAndIdNotAndEstadoIn(equipo.getId(), prestamo.getId(), ESTADOS_EN_USO)) {
                    throw new ResponseStatusException(HttpStatus.CONFLICT,
                            "No se puede dar de baja un equipo con un préstamo aprobado o entregado");
                }
                equipo.setActivo(false);
                equipo.setDisponible(false);
                equipos.save(equipo);
            }
            default -> {
                // MANTENIMIENTO: el equipo sale del catálogo y la incidencia sigue abierta.
                if (prestamos.existsByEquipoIdAndIdNotAndEstadoIn(equipo.getId(), prestamo.getId(), ESTADOS_EN_USO)) {
                    throw new ResponseStatusException(HttpStatus.CONFLICT,
                            "No se puede enviar a mantenimiento un equipo con un préstamo aprobado o entregado");
                }
                equipo.setDisponible(false);
                equipos.save(equipo);
                prestamo.setEnMantenimiento(true);
                return prestamos.save(prestamo);
            }
        }
        prestamo.setEnMantenimiento(false);
        prestamo.setIncidenciaAtendida(true);
        return prestamos.save(prestamo);
    }

    // Indica si otro préstamo del mismo equipo lo mantiene ocupado, dañado sin resolver o en mantenimiento.
    private boolean tieneBloqueoEnOtroPrestamo(Long equipoId, Long prestamoExcluidoId) {
        return prestamos.existsByEquipoIdAndIdNotAndEstadoIn(equipoId, prestamoExcluidoId, ESTADOS_EN_USO)
                || prestamos.existsByEquipoIdAndIdNotAndCondicionDevolucionAndIncidenciaAtendida(
                        equipoId, prestamoExcluidoId, CondicionEquipo.DANADO, false)
                || prestamos.existsByEquipoIdAndIdNotAndEnMantenimientoTrue(equipoId, prestamoExcluidoId);
    }

    private String limpiarObservacion(String observacion) {
        String texto = observacion == null ? null : observacion.strip();
        if (texto != null && texto.length() > 300) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "La observación admite como máximo 300 caracteres");
        }
        return texto == null || texto.isEmpty() ? null : texto;
    }

    private CondicionEquipo interpretarCondicion(String condicion) {
        if (condicion == null || condicion.isBlank()) {
            return CondicionEquipo.BUEN_ESTADO;
        }
        try {
            return CondicionEquipo.valueOf(condicion.strip().toUpperCase());
        } catch (IllegalArgumentException error) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "La condición debe ser BUEN_ESTADO, CON_DETALLES o DANADO");
        }
    }

    @Transactional(readOnly = true)
    public List<Prestamo> historial(EstadoPrestamo estado) {
        return ordenar(estado == null ? prestamos.findAll() : prestamos.findByEstado(estado));
    }

    @Transactional(readOnly = true)
    public List<Prestamo> historialPropio(String username, EstadoPrestamo estado) {
        var usuario = usuarios.findByUsername(username).orElseThrow(() ->
                new ResponseStatusException(HttpStatus.UNAUTHORIZED, "La cuenta de la sesión no existe"));
        return ordenar(prestamos.findByUsuarioId(usuario.getId()).stream()
                .filter(p -> estado == null || p.getEstado() == estado).toList());
    }

    private Prestamo buscarParaActualizar(Long id) {
        Prestamo prestamo = prestamos.findById(id).orElseThrow(() ->
                new ResponseStatusException(HttpStatus.NOT_FOUND, "Préstamo no encontrado"));
        // Releer bajo bloqueo evita que dos administradores registren la misma operación.
        entityManager.refresh(prestamo, LockModeType.PESSIMISTIC_WRITE);
        return prestamo;
    }

    private Equipo buscarEquipo(Long id) {
        Equipo equipo = equipos.findById(id).orElseThrow(() ->
                new ResponseStatusException(HttpStatus.NOT_FOUND, "Equipo no encontrado"));
        entityManager.refresh(equipo, LockModeType.PESSIMISTIC_WRITE);
        return equipo;
    }

    private void exigirEstado(Prestamo prestamo, EstadoPrestamo esperado, String mensaje) {
        if (prestamo.getEstado() != esperado) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, mensaje);
        }
    }

    private List<Prestamo> ordenar(List<Prestamo> registros) {
        return registros.stream().sorted((a, b) -> {
            int porFecha = b.getFechaSolicitud().compareTo(a.getFechaSolicitud());
            return porFecha != 0 ? porFecha : b.getId().compareTo(a.getId());
        }).toList();
    }
}
