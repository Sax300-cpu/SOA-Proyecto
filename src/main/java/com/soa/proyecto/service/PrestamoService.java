package com.soa.proyecto.service;

import com.soa.proyecto.model.Equipo;
import com.soa.proyecto.model.EstadoPrestamo;
import com.soa.proyecto.model.Prestamo;
import com.soa.proyecto.model.Usuario;
import com.soa.proyecto.repository.EquipoRepository;
import com.soa.proyecto.repository.PrestamoRepository;
import com.soa.proyecto.repository.UsuarioRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class PrestamoService {

    private final PrestamoRepository prestamoRepository;
    private final UsuarioRepository usuarioRepository;
    private final EquipoRepository equipoRepository;

    public PrestamoService(
            PrestamoRepository prestamoRepository,
            UsuarioRepository usuarioRepository,
            EquipoRepository equipoRepository) {
        this.prestamoRepository = prestamoRepository;
        this.usuarioRepository = usuarioRepository;
        this.equipoRepository = equipoRepository;
    }

    @Transactional
    public Prestamo crearSolicitud(String username, Long equipoId) {
        if (equipoId == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "El equipoId es obligatorio");
        }

        Usuario usuario = usuarioRepository.findByUsername(username)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Usuario no encontrado"));
        Equipo equipo = equipoRepository.findById(equipoId)
                .orElseThrow(EquipoNoDisponibleException::new);

        if (!Boolean.TRUE.equals(equipo.getActivo()) || !Boolean.TRUE.equals(equipo.getDisponible())) {
            throw new EquipoNoDisponibleException();
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
        Prestamo prestamo = buscarSolicitudPendiente(id);

        Equipo equipo = equipoRepository.findByIdForUpdate(prestamo.getEquipoId())
                .filter(e -> Boolean.TRUE.equals(e.getActivo())
                        && Boolean.TRUE.equals(e.getDisponible()))
                .orElseThrow(EquipoNoDisponibleException::new);

        equipo.setDisponible(false);
        equipoRepository.save(equipo);

        prestamo.setEstado(EstadoPrestamo.APROBADO);
        prestamo.setFechaAprobacion(LocalDateTime.now());
        return prestamoRepository.save(prestamo);
    }

    @Transactional
    public Prestamo rechazar(Long id) {
        Prestamo prestamo = buscarSolicitudPendiente(id);
        Equipo equipo = equipoRepository.findByIdForUpdate(prestamo.getEquipoId())
                .orElseThrow(EquipoNoDisponibleException::new);
        equipo.setDisponible(true);
        equipoRepository.save(equipo);

        prestamo.setEstado(EstadoPrestamo.RECHAZADO);
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
