package com.soa.proyecto.service;

import com.soa.proyecto.model.*;
import com.soa.proyecto.repository.*;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class DevolucionServiceTest {
    private final PrestamoRepository prestamos = mock(PrestamoRepository.class);
    private final EquipoRepository equipos = mock(EquipoRepository.class);
    private final UsuarioRepository usuarios = mock(UsuarioRepository.class);
    private final EntityManager em = mock(EntityManager.class);
    private final DevolucionService servicio = new DevolucionService(prestamos, equipos, usuarios, em);
    private Prestamo prestamo;
    private Equipo equipo;

    @BeforeEach
    void preparar() {
        prestamo = new Prestamo();
        prestamo.setId(1L);
        prestamo.setUsuarioId(2L);
        prestamo.setEquipoId(3L);
        prestamo.setEstado(EstadoPrestamo.APROBADO);
        prestamo.setFechaSolicitud(LocalDateTime.of(2026, 11, 16, 10, 0));
        equipo = new Equipo(3L, "LAP-003", "Laptop", "", false, true);
        when(prestamos.findById(1L)).thenReturn(Optional.of(prestamo));
        when(equipos.findById(3L)).thenReturn(Optional.of(equipo));
        when(prestamos.save(any(Prestamo.class))).thenAnswer(i -> i.getArgument(0));
    }

    @Test
    void entregaAprobadaRegistraFechaYMantieneEquipoOcupado() {
        assertSame(prestamo, servicio.entregar(1L));
        assertEquals(EstadoPrestamo.ENTREGADO, prestamo.getEstado());
        assertNotNull(prestamo.getFechaEntrega());
        assertNull(prestamo.getFechaDevolucion());
        assertFalse(equipo.getDisponible());
        verify(em).refresh(prestamo, LockModeType.PESSIMISTIC_WRITE);
        verify(em).refresh(equipo, LockModeType.PESSIMISTIC_WRITE);
        verify(prestamos).save(prestamo);
        verificarError(HttpStatus.BAD_REQUEST, () -> servicio.entregar(1L));
    }

    @ParameterizedTest
    @EnumSource(value = EstadoPrestamo.class, names = "APROBADO", mode = EnumSource.Mode.EXCLUDE)
    void noEntregaOtrosEstados(EstadoPrestamo estado) {
        prestamo.setEstado(estado);
        verificarError(HttpStatus.BAD_REQUEST, () -> servicio.entregar(1L));
        verify(prestamos, never()).save(any());
        verify(equipos, never()).save(any());
    }

    @Test
    void noEntregaEquipoDesactivado() {
        equipo.setActivo(false);
        verificarError(HttpStatus.BAD_REQUEST, () -> servicio.entregar(1L));
        assertEquals(EstadoPrestamo.APROBADO, prestamo.getEstado());
        verify(prestamos, never()).save(any());
    }

    @Test
    void devolucionGuardaObservacionFechaYLiberaEquipo() {
        prestamo.setEstado(EstadoPrestamo.ENTREGADO);
        prestamo.setFechaEntrega(LocalDateTime.now().minusHours(1));
        var entrega = prestamo.getFechaEntrega();
        servicio.devolver(1L, "  Pantalla rayada  ");
        assertEquals(EstadoPrestamo.DEVUELTO, prestamo.getEstado());
        assertEquals("Pantalla rayada", prestamo.getObservacion());
        assertEquals(entrega, prestamo.getFechaEntrega());
        assertNotNull(prestamo.getFechaDevolucion());
        assertTrue(equipo.getDisponible());
        verificarError(HttpStatus.BAD_REQUEST, () -> servicio.devolver(1L, null));
    }

    @Test
    void devolucionSinObservacionNoReactivaEquipoDesactivado() {
        prestamo.setEstado(EstadoPrestamo.ENTREGADO);
        equipo.setActivo(false);
        servicio.devolver(1L, "  ");
        assertNull(prestamo.getObservacion());
        assertFalse(equipo.getActivo());
        assertTrue(equipo.getDisponible());
    }

    @Test
    void aceptaObservacionNulaYLimiteDe300() {
        prestamo.setEstado(EstadoPrestamo.ENTREGADO);
        servicio.devolver(1L, null);
        assertNull(prestamo.getObservacion());
        prestamo.setEstado(EstadoPrestamo.ENTREGADO);
        servicio.devolver(1L, "a".repeat(300));
        assertEquals(300, prestamo.getObservacion().length());
    }

    @ParameterizedTest
    @EnumSource(value = EstadoPrestamo.class, names = "ENTREGADO", mode = EnumSource.Mode.EXCLUDE)
    void noDevuelveOtrosEstados(EstadoPrestamo estado) {
        prestamo.setEstado(estado);
        verificarError(HttpStatus.BAD_REQUEST, () -> servicio.devolver(1L, null));
        verify(prestamos, never()).save(any());
        verify(equipos, never()).save(any());
    }

    @Test
    void rechazaObservacionMayorALaColumnaSinModificarDatos() {
        verificarError(HttpStatus.BAD_REQUEST, () -> servicio.devolver(1L, "a".repeat(301)));
        verify(prestamos, never()).save(any());
        verify(equipos, never()).save(any());
    }

    @Test
    void informaPrestamoOEquipoInexistente() {
        verificarError(HttpStatus.NOT_FOUND, () -> servicio.entregar(99L));
        verificarError(HttpStatus.NOT_FOUND, () -> servicio.devolver(99L, null));
        when(equipos.findById(3L)).thenReturn(Optional.empty());
        verificarError(HttpStatus.NOT_FOUND, () -> servicio.entregar(1L));
        prestamo.setEstado(EstadoPrestamo.ENTREGADO);
        verificarError(HttpStatus.NOT_FOUND, () -> servicio.devolver(1L, null));
        verify(prestamos, never()).save(any());
    }

    @Test
    void historialCompletoOrdenaYPermiteFiltrarPorEstado() {
        Prestamo reciente = new Prestamo();
        reciente.setId(4L);
        reciente.setUsuarioId(5L);
        reciente.setEquipoId(6L);
        reciente.setEstado(EstadoPrestamo.DEVUELTO);
        reciente.setFechaSolicitud(prestamo.getFechaSolicitud().plusDays(1));
        when(prestamos.findAll()).thenReturn(List.of(prestamo, reciente));
        assertEquals(List.of(reciente, prestamo), servicio.historial(null));
        when(prestamos.findByEstado(EstadoPrestamo.DEVUELTO)).thenReturn(List.of(reciente));
        assertEquals(List.of(reciente), servicio.historial(EstadoPrestamo.DEVUELTO));
    }

    @Test
    void historialPropioUsaCuentaDeSesionYFiltraEstado() {
        when(usuarios.findByUsername("emilio")).thenReturn(Optional.of(
                new Usuario(2L, "Emilio", "emilio", "hash", Rol.USUARIO)));
        when(prestamos.findByUsuarioId(2L)).thenReturn(List.of(prestamo));
        assertEquals(List.of(prestamo), servicio.historialPropio("emilio", null));
        assertTrue(servicio.historialPropio("emilio", EstadoPrestamo.DEVUELTO).isEmpty());
        verify(prestamos, never()).findAll();
        verificarError(HttpStatus.UNAUTHORIZED, () -> servicio.historialPropio("otra", null));
    }

    private void verificarError(HttpStatus estado, org.junit.jupiter.api.function.Executable accion) {
        assertEquals(estado, assertThrows(ResponseStatusException.class, accion).getStatusCode());
    }
}
