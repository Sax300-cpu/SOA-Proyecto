package com.soa.proyecto.service;

import com.soa.proyecto.model.CondicionEquipo;
import com.soa.proyecto.model.Equipo;
import com.soa.proyecto.model.EstadoPrestamo;
import com.soa.proyecto.model.Prestamo;
import com.soa.proyecto.model.Usuario;
import com.soa.proyecto.repository.EquipoRepository;
import com.soa.proyecto.repository.PrestamoRepository;
import com.soa.proyecto.repository.UsuarioRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.List;

// Reglas de negocio de solicitudes: crear, aprobar (bloquea disponibilidad) y rechazar.
@Service
@RequiredArgsConstructor
public class PrestamoService {

    private static final List<EstadoPrestamo> ESTADOS_ACTIVOS = List.of(
            EstadoPrestamo.SOLICITADO, EstadoPrestamo.APROBADO, EstadoPrestamo.ENTREGADO);

    private static final List<EstadoPrestamo> ESTADOS_EN_USO = List.of(
            EstadoPrestamo.APROBADO, EstadoPrestamo.ENTREGADO);

    private final PrestamoRepository prestamoRepository;
    private final UsuarioRepository usuarioRepository;
    private final EquipoRepository equipoRepository;

    @Value("${prestamos.limite-activos:3}")
    private int limiteActivos = 3;

    public static final int PLAZO_MAXIMO_HORAS = 720;

    @Value("${prestamos.dias-plazo:3}")
    private int diasPlazo = 3;

    // El plazo de un préstamo va de 1 hora a 30 días (720 horas).
    public static int validarPlazoHoras(int horas) {
        if (horas < 1 || horas > PLAZO_MAXIMO_HORAS) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "El plazo debe estar entre 1 hora y 30 días (720 horas)");
        }
        return horas;
    }

    @Transactional
    public Prestamo crearSolicitud(String username, Long equipoId) {
        if (equipoId == null || equipoId <= 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "El identificador del equipo debe ser un número positivo");
        }

        // El bloqueo por usuario abarca la validación y el guardado, también entre equipos distintos.
        Usuario usuario = usuarioRepository.findUsuarioByUsername(username)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Usuario no encontrado"));
        if (Boolean.TRUE.equals(usuario.getBloqueado())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "Tu cuenta está bloqueada. Acércate a la administración");
        }
        Equipo equipo = equipoRepository.findById(equipoId)
                .orElseThrow(() -> new EquipoNoDisponibleException());

        if (!Boolean.TRUE.equals(equipo.getActivo()) || !Boolean.TRUE.equals(equipo.getDisponible())) {
            throw new EquipoNoDisponibleException();
        }
        if (prestamoRepository.existsByUsuarioIdAndEquipoIdAndEstadoIn(
                usuario.getId(), equipo.getId(), ESTADOS_ACTIVOS)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Ya tienes una solicitud o préstamo activo para este equipo");
        }
        if (prestamoRepository.countByUsuarioIdAndEstadoIn(usuario.getId(), ESTADOS_ACTIVOS) >= limiteActivos) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Has alcanzado el límite de " + limiteActivos + " solicitudes o préstamos activos");
        }

        Prestamo prestamo = new Prestamo();
        prestamo.setUsuarioId(usuario.getId());
        prestamo.setEquipoId(equipo.getId());
        prestamo.setEstado(EstadoPrestamo.SOLICITADO);
        prestamo.setFechaSolicitud(LocalDateTime.now());
        return prestamoRepository.save(prestamo);
    }

    @Transactional(readOnly = true)
    public List<Prestamo> historialDe(String username) {
        Usuario usuario = usuarioRepository.findByUsername(username)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Usuario no encontrado"));
        return prestamoRepository.findByUsuarioId(usuario.getId());
    }

    @Transactional(readOnly = true)
    public List<Prestamo> buscarPorEstado(EstadoPrestamo estado) {
        return prestamoRepository.findByEstado(estado);
    }

    @Transactional
    public Prestamo aprobar(Long id) {
        return aprobar(id, null);
    }

    // El plazo de devolución (en horas) lo fija el administrador al aprobar y empieza a contar al entregar.
    // Si no se indica se usa el predeterminado de la configuración.
    @Transactional
    public Prestamo aprobar(Long id, Integer plazoHoras) {
        int horas = plazoHoras == null ? diasPlazo * 24 : validarPlazoHoras(plazoHoras);
        Prestamo prestamo = buscarSolicitudPendiente(id);

        Equipo equipo = equipoRepository.findEquipoById(prestamo.getEquipoId())
                .filter(e -> Boolean.TRUE.equals(e.getActivo())
                        && Boolean.TRUE.equals(e.getDisponible()))
                .orElseThrow(() -> new EquipoNoDisponibleException());

        equipo.setDisponible(false);
        equipoRepository.save(equipo);

        prestamo.setEstado(EstadoPrestamo.APROBADO);
        prestamo.setFechaAprobacion(LocalDateTime.now());
        prestamo.setPlazoHoras(horas);
        return prestamoRepository.save(prestamo);
    }

    @Transactional
    public Prestamo rechazar(Long id) {
        // Rechazar solo cambia el estado de la solicitud; no altera la disponibilidad
        // del equipo, que se controla en aprobar (false) y devolver (true).
        Prestamo prestamo = buscarSolicitudPendiente(id);
        prestamo.setEstado(EstadoPrestamo.RECHAZADO);
        return prestamoRepository.save(prestamo);
    }

    // Anula un préstamo aprobado que aún no se entregó (el usuario no fue por el equipo): queda RECHAZADO
    // con el motivo y el equipo reservado vuelve al catálogo.
    @Transactional
    public Prestamo anular(Long id, String motivo) {
        String texto = motivo == null ? null : motivo.strip();
        if (texto != null && texto.length() > 300) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "El motivo admite como máximo 300 caracteres");
        }
        Prestamo prestamo = prestamoRepository.findByIdForUpdate(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Solicitud no encontrada"));
        if (prestamo.getEstado() != EstadoPrestamo.APROBADO) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Solo se puede anular un préstamo aprobado que aún no se entregó");
        }
        Equipo equipo = equipoRepository.findEquipoById(prestamo.getEquipoId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Equipo no encontrado"));
        boolean bloqueadoPorOtro = prestamoRepository.existsByEquipoIdAndIdNotAndEstadoIn(
                equipo.getId(), prestamo.getId(), ESTADOS_EN_USO)
                || prestamoRepository.existsByEquipoIdAndIdNotAndCondicionDevolucionAndIncidenciaAtendida(
                        equipo.getId(), prestamo.getId(), CondicionEquipo.DANADO, false)
                || prestamoRepository.existsByEquipoIdAndIdNotAndEnMantenimientoTrue(
                        equipo.getId(), prestamo.getId());
        equipo.setDisponible(Boolean.TRUE.equals(equipo.getActivo()) && !bloqueadoPorOtro);
        equipoRepository.save(equipo);

        prestamo.setEstado(EstadoPrestamo.RECHAZADO);
        prestamo.setObservacion(texto == null || texto.isEmpty() ? "Anulado por el administrador" : texto);
        return prestamoRepository.save(prestamo);
    }

    private Prestamo buscarSolicitudPendiente(Long id) {
        Prestamo prestamo = prestamoRepository.findByIdForUpdate(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Solicitud no encontrada"));
        if (prestamo.getEstado() != EstadoPrestamo.SOLICITADO) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT, "Solo se pueden aprobar o rechazar solicitudes pendientes");
        }
        return prestamo;
    }
}
