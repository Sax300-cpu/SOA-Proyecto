package com.soa.proyecto.service;

import com.soa.proyecto.model.Equipo;
import com.soa.proyecto.model.EstadoPrestamo;
import com.soa.proyecto.model.Prestamo;
import com.soa.proyecto.model.Usuario;
import com.soa.proyecto.repository.EquipoRepository;
import com.soa.proyecto.repository.PrestamoRepository;
import com.soa.proyecto.repository.UsuarioRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.server.ResponseStatusException;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class PrestamoServiceTest {

    private final PrestamoRepository prestamos = mock(PrestamoRepository.class);
    private final UsuarioRepository usuarios = mock(UsuarioRepository.class);
    private final EquipoRepository equipos = mock(EquipoRepository.class);
    private final PrestamoService servicio = new PrestamoService(prestamos, usuarios, equipos);
    private final Usuario usuario = new Usuario(2L, "Usuario", "usuario", "hash", null);
    private final Equipo equipo = new Equipo(3L, "Laptop", "", false, true);
    private final Prestamo prestamo = new Prestamo();

    @BeforeEach
    void preparar() {
        prestamo.setId(1L);
        prestamo.setUsuarioId(usuario.getId());
        prestamo.setEquipoId(equipo.getId());
        prestamo.setEstado(EstadoPrestamo.SOLICITADO);

        when(usuarios.findByUsername("usuario")).thenReturn(Optional.of(usuario));
        when(equipos.findById(3L)).thenReturn(Optional.of(equipo));
        when(equipos.findByIdForUpdate(3L)).thenReturn(Optional.of(equipo));
        when(prestamos.findByIdForUpdate(1L)).thenReturn(Optional.of(prestamo));
        when(prestamos.save(any(Prestamo.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(equipos.save(any(Equipo.class))).thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    void crearSolicitudFallaConConflictoSiEquipoNoExisteOEstaInactivoONoDisponible() {
        when(equipos.findById(3L)).thenReturn(Optional.empty());
        assertThrows(EquipoNoDisponibleException.class, () -> servicio.crearSolicitud("usuario", 3L));

        when(equipos.findById(3L)).thenReturn(Optional.of(equipo));
        equipo.setActivo(false);
        assertThrows(EquipoNoDisponibleException.class, () -> servicio.crearSolicitud("usuario", 3L));

        equipo.setActivo(true);
        equipo.setDisponible(false);
        assertThrows(EquipoNoDisponibleException.class, () -> servicio.crearSolicitud("usuario", 3L));

        assertEquals(HttpStatus.CONFLICT,
                EquipoNoDisponibleException.class.getAnnotation(ResponseStatus.class).value());
        verify(prestamos, never()).save(any());
    }

    @Test
    void aprobarSolicitudFallaSiEquipoNoExisteEstaInactivoONoDisponible() {
        when(equipos.findByIdForUpdate(3L)).thenReturn(Optional.empty());
        assertThrows(EquipoNoDisponibleException.class, () -> servicio.aprobar(1L));

        when(equipos.findByIdForUpdate(3L)).thenReturn(Optional.of(equipo));
        equipo.setActivo(false);
        assertThrows(EquipoNoDisponibleException.class, () -> servicio.aprobar(1L));

        equipo.setActivo(true);
        equipo.setDisponible(false);
        assertThrows(EquipoNoDisponibleException.class, () -> servicio.aprobar(1L));

        verify(equipos, times(3)).findByIdForUpdate(3L);
        verify(prestamos, never()).save(any());
    }

    @Test
    void rechazarBloqueaYLiberaElEquipoAntesDeGuardarElRechazo() {
        equipo.setDisponible(false);

        servicio.rechazar(1L);

        var orden = inOrder(prestamos, equipos);
        orden.verify(prestamos).findByIdForUpdate(1L);
        orden.verify(equipos).findByIdForUpdate(3L);
        orden.verify(equipos).save(equipo);
        orden.verify(prestamos).save(prestamo);
        assertTrue(equipo.getDisponible());
        assertEquals(EstadoPrestamo.RECHAZADO, prestamo.getEstado());
    }

    @Test
    void rechazoPosteriorAprobacionNoPuedeAplicarse() {
        prestamo.setEstado(EstadoPrestamo.APROBADO);

        ResponseStatusException error = assertThrows(
                ResponseStatusException.class, () -> servicio.rechazar(1L));

        assertEquals(HttpStatus.CONFLICT, error.getStatusCode());
        verify(equipos, never()).findByIdForUpdate(any());
    }
}
