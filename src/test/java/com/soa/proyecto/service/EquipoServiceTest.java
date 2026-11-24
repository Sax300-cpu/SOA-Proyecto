package com.soa.proyecto.service;

import com.soa.proyecto.dto.EquipoRequest;
import com.soa.proyecto.model.CondicionEquipo;
import com.soa.proyecto.model.Equipo;
import com.soa.proyecto.model.EstadoPrestamo;
import com.soa.proyecto.repository.EquipoRepository;
import com.soa.proyecto.repository.PrestamoRepository;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class EquipoServiceTest {
    private final EquipoRepository repository = mock(EquipoRepository.class);
    private final PrestamoRepository prestamos = mock(PrestamoRepository.class);
    private final EquipoService servicio = new EquipoService(repository, prestamos);

    @Test
    void crearNormalizaDatosYArmaCodigoConPrefijoDelNombre() {
        when(repository.save(any())).thenAnswer(invocacion -> invocacion.getArgument(0));
        Equipo creado = servicio.crear(new EquipoRequest("  Laptop  ", "  Para clases  ", null));
        assertEquals("LAP-001", creado.getCodigo());
        assertEquals("Laptop", creado.getNombre());
        assertEquals("Para clases", creado.getDescripcion());
        assertEquals(true, creado.getActivo());
        assertEquals(true, creado.getDisponible());
    }

    @Test
    void elNumeroContinuaDespuesDelMayorDelMismoPrefijo() {
        when(repository.findByCodigoStartingWith("CAB-")).thenReturn(List.of(
                new Equipo(1L, "CAB-001", "Cable", null, true, true),
                new Equipo(2L, "CAB-009", "Cable HDMI", null, true, false),
                new Equipo(3L, "CAB-004", "Cable VGA", null, true, false)));
        when(repository.save(any())).thenAnswer(invocacion -> invocacion.getArgument(0));
        assertEquals("CAB-010", servicio.crear(new EquipoRequest("Cable USB", null, null)).getCodigo());
    }

    @Test
    void cadaPrefijoLlevaSuPropiaNumeracion() {
        when(repository.findByCodigoStartingWith("CAB-")).thenReturn(List.of(
                new Equipo(1L, "CAB-020", "Cable", null, true, true)));
        when(repository.save(any())).thenAnswer(invocacion -> invocacion.getArgument(0));
        assertEquals("PRO-001", servicio.crear(new EquipoRequest("Proyector", null, null)).getCodigo());
    }

    @Test
    void elPrefijoEnviadoTienePrioridadYSeGuardaEnMayusculas() {
        when(repository.save(any())).thenAnswer(invocacion -> invocacion.getArgument(0));
        assertEquals("ADP-001", servicio.crear(new EquipoRequest("Cargador universal", null, " adp ")).getCodigo());
    }

    @Test
    void prefijoInvalidoDevuelve400() {
        for (String prefijo : new String[]{"AB", "ABCD", "A1B", "A-B", "12 "}) {
            assertEquals(HttpStatus.BAD_REQUEST, assertThrows(ResponseStatusException.class,
                    () -> servicio.crear(new EquipoRequest("Cable", null, prefijo))).getStatusCode());
        }
        verify(repository, never()).save(any());
    }

    @Test
    void prefijoDesdeNombreQuitaTildesSimbolosYUsaRespaldo() {
        assertEquals("CAM", EquipoService.prefijoDesdeNombre("Cámara web"));
        assertEquals("PRO", EquipoService.prefijoDesdeNombre("  proyector Epson"));
        // Solo cuentan las letras: "3D Printer" -> "DPrinter" -> DPR (el admin puede cambiarlo en el modal).
        assertEquals("DPR", EquipoService.prefijoDesdeNombre("3D Printer"));
        assertEquals("EQP", EquipoService.prefijoDesdeNombre("TV"));
        assertEquals("EQP", EquipoService.prefijoDesdeNombre("123"));
    }

    @Test
    void altaSimultaneaConElMismoCodigoDevuelve409() {
        when(repository.save(any())).thenThrow(new DataIntegrityViolationException("duplicado"));
        assertEquals(HttpStatus.CONFLICT, assertThrows(ResponseStatusException.class,
                () -> servicio.crear(new EquipoRequest("Cable", null, null))).getStatusCode());
    }

    @Test
    void validaNombreYLongitudesAntesDeGuardar() {
        for (EquipoRequest datos : new EquipoRequest[]{null, new EquipoRequest(null, null, null),
                new EquipoRequest("   ", null, null), new EquipoRequest("a".repeat(51), null, null),
                new EquipoRequest("Laptop", "a".repeat(201), null)}) {
            assertEquals(HttpStatus.BAD_REQUEST, assertThrows(ResponseStatusException.class,
                    () -> servicio.crear(datos)).getStatusCode());
        }
        verify(repository, never()).save(any());
    }

    @Test
    void admiteLimitesYDescripcionOpcional() {
        when(repository.save(any())).thenAnswer(invocacion -> invocacion.getArgument(0));
        assertEquals(200, servicio.crear(new EquipoRequest("a".repeat(50), "b".repeat(200), null)).getDescripcion().length());
        Equipo sinDesc = servicio.crear(new EquipoRequest("Laptop", null, null));
        assertNull(sinDesc.getDescripcion());
        assertEquals("LAP-001", sinDesc.getCodigo());
    }

    @Test
    void editarNoCambiaElCodigoAunqueCambieElNombreOElPrefijo() {
        Equipo equipo = new Equipo(7L, "CAB-003", "Cable", null, true, true);
        when(repository.findEquipoById(7L)).thenReturn(Optional.of(equipo));
        when(repository.save(any())).thenAnswer(invocacion -> invocacion.getArgument(0));
        Equipo editado = servicio.editar(7L, new EquipoRequest("Proyector", "Aula", "PRO"));
        assertEquals("CAB-003", editado.getCodigo());
        assertEquals("Proyector", editado.getNombre());
    }

    @Test
    void editarEquipoAntiguoSinCodigoLeAsignaUnoAutomatico() {
        Equipo sinCodigo = new Equipo(5L, null, "Proyector", null, true, true);
        when(repository.findEquipoById(5L)).thenReturn(Optional.of(sinCodigo));
        when(repository.save(any())).thenAnswer(invocacion -> invocacion.getArgument(0));
        Equipo editado = servicio.editar(5L, new EquipoRequest("Proyector Epson", "Aula 3", null));
        assertEquals("EQUIPO-005", editado.getCodigo());
        assertEquals("Proyector Epson", editado.getNombre());
    }

    @Test
    void listadoExcluyeInactivosYFiltraDisponibilidad() {
        Equipo disponible = equipo(true, true);
        Equipo ocupado = equipo(true, false);
        when(repository.findAll()).thenReturn(List.of(disponible, ocupado, equipo(false, true)));
        assertEquals(List.of(disponible, ocupado), servicio.listar(false));
        assertEquals(List.of(disponible), servicio.listar(true));
    }

    @Test
    void editarNoLiberaUnEquipoReservado() {
        Equipo ocupado = equipo(true, false);
        when(repository.findEquipoById(1L)).thenReturn(Optional.of(ocupado));
        when(repository.save(any())).thenAnswer(invocacion -> invocacion.getArgument(0));
        Equipo editado = servicio.editar(1L, new EquipoRequest("Nuevo nombre", null, null));
        assertEquals(false, editado.getDisponible());
        assertEquals(true, editado.getActivo());
        assertEquals(1L, editado.getId());
        assertEquals("LAP-001", editado.getCodigo());
    }

    @Test
    void desactivarConservaRegistroYDisponibilidadYEsRepetible() {
        Equipo ocupado = equipo(true, false);
        when(repository.findEquipoById(1L)).thenReturn(Optional.of(ocupado));
        when(repository.save(any())).thenAnswer(invocacion -> invocacion.getArgument(0));
        servicio.desactivar(1L);
        servicio.desactivar(1L);
        assertEquals(false, ocupado.getActivo());
        assertEquals(false, ocupado.getDisponible());
        assertEquals(1L, ocupado.getId());
        verify(repository, never()).delete(any());
    }

    @Test
    void desactivarRechazaEquipoAprobadoPendienteDeEntrega() {
        Equipo reservado = equipo(true, false);
        when(repository.findEquipoById(1L)).thenReturn(Optional.of(reservado));
        when(prestamos.existsByEquipoIdAndEstado(1L, EstadoPrestamo.APROBADO)).thenReturn(true);

        ResponseStatusException error = assertThrows(ResponseStatusException.class, () -> servicio.desactivar(1L));

        assertEquals(HttpStatus.CONFLICT, error.getStatusCode());
        assertTrue(reservado.getActivo());
        verify(repository, never()).save(any());
    }

    @Test
    void listarDesactivadosDevuelveSoloLosInactivos() {
        Equipo activo = equipo(true, true);
        Equipo inactivo = equipo(false, true);
        when(repository.findAll()).thenReturn(List.of(activo, inactivo));
        assertEquals(List.of(inactivo), servicio.listarDesactivados());
    }

    @Test
    void activarUnEquipoLibreLoDejaActivoYDisponible() {
        Equipo desactivado = equipo(false, false);
        when(repository.findEquipoById(1L)).thenReturn(Optional.of(desactivado));
        when(repository.save(any())).thenAnswer(invocacion -> invocacion.getArgument(0));
        Equipo activado = servicio.activar(1L);
        assertTrue(activado.getActivo());
        assertTrue(activado.getDisponible());
    }

    @Test
    void activarUnEquipoEnUsoLoDejaNoDisponible() {
        Equipo desactivado = equipo(false, false);
        when(repository.findEquipoById(1L)).thenReturn(Optional.of(desactivado));
        when(repository.save(any())).thenAnswer(invocacion -> invocacion.getArgument(0));
        when(prestamos.existsByEquipoIdAndEstadoIn(eq(1L), any())).thenReturn(true);
        Equipo activado = servicio.activar(1L);
        assertTrue(activado.getActivo());
        assertFalse(activado.getDisponible());
    }

    @Test
    void activarUnEquipoConDanoSinResolverLoDejaNoDisponible() {
        Equipo desactivado = equipo(false, false);
        when(repository.findEquipoById(1L)).thenReturn(Optional.of(desactivado));
        when(repository.save(any())).thenAnswer(invocacion -> invocacion.getArgument(0));
        when(prestamos.existsByEquipoIdAndCondicionDevolucionAndIncidenciaAtendida(
                1L, CondicionEquipo.DANADO, false)).thenReturn(true);
        Equipo activado = servicio.activar(1L);
        assertTrue(activado.getActivo());
        assertFalse(activado.getDisponible());
    }

    @Test
    void activarUnEquipoEnMantenimientoLoDejaNoDisponible() {
        Equipo desactivado = equipo(false, false);
        when(repository.findEquipoById(1L)).thenReturn(Optional.of(desactivado));
        when(repository.save(any())).thenAnswer(invocacion -> invocacion.getArgument(0));
        when(prestamos.existsByEquipoIdAndEnMantenimientoTrue(1L)).thenReturn(true);
        Equipo activado = servicio.activar(1L);
        assertTrue(activado.getActivo());
        assertFalse(activado.getDisponible());
    }

    @Test
    void activarUnEquipoYaActivoNoCambiaNada() {
        Equipo activo = equipo(true, false);
        when(repository.findEquipoById(1L)).thenReturn(Optional.of(activo));
        assertSame(activo, servicio.activar(1L));
        assertFalse(activo.getDisponible());
        verify(repository, never()).save(any());
    }

    @Test
    void activarUnEquipoInexistenteDevuelve404() {
        assertEquals(HttpStatus.NOT_FOUND, assertThrows(ResponseStatusException.class,
                () -> servicio.activar(9L)).getStatusCode());
    }

    @Test
    void inexistenteOInactivoDevuelve404() {
        when(repository.findById(1L)).thenReturn(Optional.of(equipo(false, true)));
        when(repository.findEquipoById(1L)).thenReturn(Optional.of(equipo(false, true)));
        assertEquals(HttpStatus.NOT_FOUND, assertThrows(ResponseStatusException.class, () -> servicio.detalle(1L)).getStatusCode());
        assertEquals(HttpStatus.NOT_FOUND, assertThrows(ResponseStatusException.class,
                () -> servicio.editar(1L, new EquipoRequest("Laptop", null, null))).getStatusCode());
        assertEquals(HttpStatus.NOT_FOUND, assertThrows(ResponseStatusException.class, () -> servicio.desactivar(9L)).getStatusCode());
    }

    private Equipo equipo(boolean activo, boolean disponible) {
        return new Equipo(1L, "LAP-001", "Laptop", null, disponible, activo);
    }
}
