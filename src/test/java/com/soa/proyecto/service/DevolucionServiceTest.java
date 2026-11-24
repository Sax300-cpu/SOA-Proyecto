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
        assertEquals(prestamo.getFechaEntrega().plusDays(3), prestamo.getFechaLimite());
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
        prestamo.setFechaLimite(prestamo.getFechaEntrega().plusDays(3));
        var entrega = prestamo.getFechaEntrega();
        var limite = prestamo.getFechaLimite();
        servicio.devolver(1L, "  Pantalla rayada  ");
        assertEquals(EstadoPrestamo.DEVUELTO, prestamo.getEstado());
        assertEquals("Pantalla rayada", prestamo.getObservacion());
        assertEquals(entrega, prestamo.getFechaEntrega());
        assertEquals(limite, prestamo.getFechaLimite());
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
    void laEntregaUsaElPlazoEnHorasEnviadoPorElAdmin() {
        servicio.entregar(1L, null, 2);
        assertEquals(2, prestamo.getPlazoHoras());
        assertEquals(prestamo.getFechaEntrega().plusHours(2), prestamo.getFechaLimite());
    }

    @Test
    void laEntregaUsaElPlazoFijadoAlAprobarSiNoSeEnvia() {
        prestamo.setPlazoHoras(5);
        servicio.entregar(1L, null, null);
        assertEquals(5, prestamo.getPlazoHoras());
        assertEquals(prestamo.getFechaEntrega().plusHours(5), prestamo.getFechaLimite());
    }

    @Test
    void elPlazoEnviadoEnLaEntregaTienePrioridadSobreElAprobado() {
        prestamo.setPlazoHoras(5);
        servicio.entregar(1L, null, 48);
        assertEquals(48, prestamo.getPlazoHoras());
        assertEquals(prestamo.getFechaEntrega().plusHours(48), prestamo.getFechaLimite());
    }

    @Test
    void plazoDeEntregaFueraDeRangoDevuelve400SinCambiarNada() {
        verificarError(HttpStatus.BAD_REQUEST, () -> servicio.entregar(1L, null, 0));
        verificarError(HttpStatus.BAD_REQUEST, () -> servicio.entregar(1L, null, 721));
        assertEquals(EstadoPrestamo.APROBADO, prestamo.getEstado());
        verify(prestamos, never()).save(any());
    }

    @Test
    void entregaGuardaLaObservacionOpcionalYRechazaMasDe300() {
        servicio.entregar(1L, "  Con un rayón en la tapa  ");
        assertEquals("Con un rayón en la tapa", prestamo.getObservacionEntrega());

        prestamo.setEstado(EstadoPrestamo.APROBADO);
        servicio.entregar(1L, "   ");
        assertNull(prestamo.getObservacionEntrega());

        prestamo.setEstado(EstadoPrestamo.APROBADO);
        verificarError(HttpStatus.BAD_REQUEST, () -> servicio.entregar(1L, "a".repeat(301)));
        assertEquals(EstadoPrestamo.APROBADO, prestamo.getEstado());
    }

    @Test
    void devolucionEnBuenEstadoNoAbreIncidencia() {
        prestamo.setEstado(EstadoPrestamo.ENTREGADO);
        servicio.devolver(1L, null, "BUEN_ESTADO");
        assertEquals(CondicionEquipo.BUEN_ESTADO, prestamo.getCondicionDevolucion());
        assertNull(prestamo.getIncidenciaAtendida());
        assertTrue(equipo.getDisponible());
    }

    @Test
    void sinCondicionSeAsumeBuenEstado() {
        prestamo.setEstado(EstadoPrestamo.ENTREGADO);
        servicio.devolver(1L, null, null);
        assertEquals(CondicionEquipo.BUEN_ESTADO, prestamo.getCondicionDevolucion());
    }

    @Test
    void conDetallesAbreIncidenciaPeroElEquipoSigueDisponible() {
        prestamo.setEstado(EstadoPrestamo.ENTREGADO);
        servicio.devolver(1L, "Falta un tornillo", "CON_DETALLES");
        assertEquals(CondicionEquipo.CON_DETALLES, prestamo.getCondicionDevolucion());
        assertEquals(false, prestamo.getIncidenciaAtendida());
        assertTrue(equipo.getDisponible());
    }

    @Test
    void danadoAbreIncidenciaYDejaElEquipoNoDisponible() {
        prestamo.setEstado(EstadoPrestamo.ENTREGADO);
        servicio.devolver(1L, "Pantalla rota", "danado");
        assertEquals(CondicionEquipo.DANADO, prestamo.getCondicionDevolucion());
        assertEquals(false, prestamo.getIncidenciaAtendida());
        assertFalse(equipo.getDisponible());
        assertEquals("Pantalla rota", prestamo.getObservacion());
    }

    @Test
    void conDetallesODanadoExigenObservacionYCondicionValida() {
        prestamo.setEstado(EstadoPrestamo.ENTREGADO);
        verificarError(HttpStatus.BAD_REQUEST, () -> servicio.devolver(1L, null, "DANADO"));
        verificarError(HttpStatus.BAD_REQUEST, () -> servicio.devolver(1L, "  ", "CON_DETALLES"));
        verificarError(HttpStatus.BAD_REQUEST, () -> servicio.devolver(1L, "algo", "ROTO"));
        assertEquals(EstadoPrestamo.ENTREGADO, prestamo.getEstado());
        verify(prestamos, never()).save(any());
        verify(equipos, never()).save(any());
    }

    @Test
    void repararUnEquipoDanadoLoVuelveAlCatalogo() {
        devueltoConIncidencia(CondicionEquipo.DANADO);
        servicio.atenderIncidencia(1L, "reparado");
        assertTrue(equipo.getDisponible());
        assertTrue(equipo.getActivo());
        assertEquals(true, prestamo.getIncidenciaAtendida());
    }

    @Test
    void darDeBajaUnEquipoDanadoLoDesactiva() {
        devueltoConIncidencia(CondicionEquipo.DANADO);
        servicio.atenderIncidencia(1L, "BAJA");
        assertFalse(equipo.getActivo());
        assertFalse(equipo.getDisponible());
        assertEquals(true, prestamo.getIncidenciaAtendida());
    }

    @Test
    void atenderUnaIncidenciaConDetallesSoloCierraElAviso() {
        devueltoConIncidencia(CondicionEquipo.CON_DETALLES);
        equipo.setDisponible(true);
        servicio.atenderIncidencia(1L, "ATENDIDA");
        assertTrue(equipo.getDisponible());
        assertEquals(true, prestamo.getIncidenciaAtendida());
        verify(equipos, never()).save(any());
    }

    @Test
    void rechazaAccionesQueNoCorrespondenALaCondicion() {
        devueltoConIncidencia(CondicionEquipo.DANADO);
        verificarError(HttpStatus.BAD_REQUEST, () -> servicio.atenderIncidencia(1L, "ATENDIDA"));
        verificarError(HttpStatus.BAD_REQUEST, () -> servicio.atenderIncidencia(1L, null));
        devueltoConIncidencia(CondicionEquipo.CON_DETALLES);
        verificarError(HttpStatus.BAD_REQUEST, () -> servicio.atenderIncidencia(1L, "REPARADO"));
        assertEquals(false, prestamo.getIncidenciaAtendida());
    }

    @Test
    void noSePuedeAtenderUnaIncidenciaInexistenteNiRepetida() {
        prestamo.setEstado(EstadoPrestamo.DEVUELTO);
        prestamo.setCondicionDevolucion(CondicionEquipo.BUEN_ESTADO);
        verificarError(HttpStatus.CONFLICT, () -> servicio.atenderIncidencia(1L, "ATENDIDA"));

        devueltoConIncidencia(CondicionEquipo.DANADO);
        servicio.atenderIncidencia(1L, "REPARADO");
        verificarError(HttpStatus.CONFLICT, () -> servicio.atenderIncidencia(1L, "REPARADO"));

        prestamo.setEstado(EstadoPrestamo.ENTREGADO);
        prestamo.setIncidenciaAtendida(false);
        verificarError(HttpStatus.CONFLICT, () -> servicio.atenderIncidencia(1L, "REPARADO"));
    }

    @Test
    void mantenimientoSacaElEquipoDelCatalogoYDejaLaIncidenciaAbierta() {
        for (CondicionEquipo condicion : new CondicionEquipo[]{CondicionEquipo.DANADO, CondicionEquipo.CON_DETALLES}) {
            devueltoConIncidencia(condicion);
            equipo.setDisponible(condicion == CondicionEquipo.CON_DETALLES);
            servicio.atenderIncidencia(1L, "mantenimiento");
            assertFalse(equipo.getDisponible());
            assertTrue(equipo.getActivo());
            assertEquals(true, prestamo.getEnMantenimiento());
            assertEquals(false, prestamo.getIncidenciaAtendida());
            prestamo.setEnMantenimiento(null);
        }
    }

    @Test
    void desdeMantenimientoSePuedeRepararODarDeBajaPeroNoVolverAEnviar() {
        devueltoConIncidencia(CondicionEquipo.CON_DETALLES);
        servicio.atenderIncidencia(1L, "MANTENIMIENTO");
        verificarError(HttpStatus.BAD_REQUEST, () -> servicio.atenderIncidencia(1L, "MANTENIMIENTO"));
        verificarError(HttpStatus.BAD_REQUEST, () -> servicio.atenderIncidencia(1L, "ATENDIDA"));

        servicio.atenderIncidencia(1L, "REPARADO");
        assertTrue(equipo.getDisponible());
        assertEquals(false, prestamo.getEnMantenimiento());
        assertEquals(true, prestamo.getIncidenciaAtendida());
    }

    @Test
    void desdeMantenimientoSePuedeDarDeBaja() {
        devueltoConIncidencia(CondicionEquipo.DANADO);
        servicio.atenderIncidencia(1L, "MANTENIMIENTO");
        servicio.atenderIncidencia(1L, "BAJA");
        assertFalse(equipo.getActivo());
        assertFalse(equipo.getDisponible());
        assertEquals(false, prestamo.getEnMantenimiento());
        assertEquals(true, prestamo.getIncidenciaAtendida());
    }

    @Test
    void darDeBajaUnEquipoConDetallesLoDesactiva() {
        devueltoConIncidencia(CondicionEquipo.CON_DETALLES);
        equipo.setDisponible(true);
        servicio.atenderIncidencia(1L, "BAJA");
        assertFalse(equipo.getActivo());
        assertFalse(equipo.getDisponible());
        assertEquals(true, prestamo.getIncidenciaAtendida());
    }

    @Test
    void devolucionNoLiberaEquipoSiOtroPrestamoLoMantieneBloqueado() {
        prestamo.setEstado(EstadoPrestamo.ENTREGADO);
        when(prestamos.existsByEquipoIdAndIdNotAndEnMantenimientoTrue(3L, 1L)).thenReturn(true);

        servicio.devolver(1L, null, "BUEN_ESTADO");

        assertFalse(equipo.getDisponible());
        assertEquals(EstadoPrestamo.DEVUELTO, prestamo.getEstado());
    }

    @Test
    void repararNoLiberaEquipoSiEstaEnUsoOTieneOtroDanoAbierto() {
        devueltoConIncidencia(CondicionEquipo.DANADO);
        when(prestamos.existsByEquipoIdAndIdNotAndEstadoIn(eq(3L), eq(1L), any())).thenReturn(true);

        servicio.atenderIncidencia(1L, "REPARADO");

        assertFalse(equipo.getDisponible());
        assertEquals(true, prestamo.getIncidenciaAtendida());

        devueltoConIncidencia(CondicionEquipo.DANADO);
        when(prestamos.existsByEquipoIdAndIdNotAndEstadoIn(eq(3L), eq(1L), any())).thenReturn(false);
        when(prestamos.existsByEquipoIdAndIdNotAndCondicionDevolucionAndIncidenciaAtendida(
                3L, 1L, CondicionEquipo.DANADO, false)).thenReturn(true);

        servicio.atenderIncidencia(1L, "REPARADO");

        assertFalse(equipo.getDisponible());
        assertEquals(true, prestamo.getIncidenciaAtendida());
    }

    @Test
    void mantenimientoYBajaFallanCon409SiElEquipoEstaEnUsoPorOtroPrestamo() {
        devueltoConIncidencia(CondicionEquipo.CON_DETALLES);
        when(prestamos.existsByEquipoIdAndIdNotAndEstadoIn(eq(3L), eq(1L), any())).thenReturn(true);

        verificarError(HttpStatus.CONFLICT, () -> servicio.atenderIncidencia(1L, "MANTENIMIENTO"));
        verificarError(HttpStatus.CONFLICT, () -> servicio.atenderIncidencia(1L, "BAJA"));

        assertTrue(equipo.getActivo());
        assertEquals(false, prestamo.getIncidenciaAtendida());
        verify(prestamos, never()).save(any());
        verify(equipos, never()).save(any());
    }

    private void devueltoConIncidencia(CondicionEquipo condicion) {
        prestamo.setEstado(EstadoPrestamo.DEVUELTO);
        prestamo.setCondicionDevolucion(condicion);
        prestamo.setIncidenciaAtendida(false);
        equipo.setActivo(true);
        equipo.setDisponible(false);
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
