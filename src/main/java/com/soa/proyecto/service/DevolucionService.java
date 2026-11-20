package com.soa.proyecto.service;

import com.soa.proyecto.model.Equipo;
import com.soa.proyecto.model.EstadoPrestamo;
import com.soa.proyecto.model.Prestamo;
import com.soa.proyecto.repository.EquipoRepository;
import com.soa.proyecto.repository.PrestamoRepository;
import com.soa.proyecto.repository.UsuarioRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class DevolucionService {

    private final PrestamoRepository prestamos;
    private final EquipoRepository equipos;
    private final UsuarioRepository usuarios;
    private final EntityManager entityManager;

    @Transactional
    public Prestamo entregar(Long id) {
        Prestamo prestamo = buscarParaActualizar(id);
        exigirEstado(prestamo, EstadoPrestamo.APROBADO, "Solo se puede entregar un préstamo aprobado");
        Equipo equipo = buscarEquipo(prestamo.getEquipoId());
        if (!Boolean.TRUE.equals(equipo.getActivo())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "El equipo está desactivado");
        }
        equipo.setDisponible(false);
        prestamo.setEstado(EstadoPrestamo.ENTREGADO);
        prestamo.setFechaEntrega(LocalDateTime.now());
        equipos.save(equipo);
        return prestamos.save(prestamo);
    }

    @Transactional
    public Prestamo devolver(Long id, String observacion) {
        String texto = observacion == null ? null : observacion.strip();
        if (texto != null && texto.length() > 300) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "La observación admite como máximo 300 caracteres");
        }
        Prestamo prestamo = buscarParaActualizar(id);
        exigirEstado(prestamo, EstadoPrestamo.ENTREGADO, "Solo se puede devolver un préstamo entregado");
        Equipo equipo = buscarEquipo(prestamo.getEquipoId());
        prestamo.setEstado(EstadoPrestamo.DEVUELTO);
        prestamo.setFechaDevolucion(LocalDateTime.now());
        prestamo.setObservacion(texto == null || texto.isEmpty() ? null : texto);
        // La devolución no reactiva equipos desactivados por el administrador.
        equipo.setDisponible(true);
        equipos.save(equipo);
        return prestamos.save(prestamo);
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
