package com.soa.proyecto.service;

import com.soa.proyecto.dto.EquipoRequest;
import com.soa.proyecto.model.Equipo;
import com.soa.proyecto.model.EstadoPrestamo;
import com.soa.proyecto.repository.EquipoRepository;
import com.soa.proyecto.repository.PrestamoRepository;
import org.junit.jupiter.api.Test;
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
    void crearNormalizaDatosYGeneraCodigoAutomatico() {
        when(repository.save(any())).thenAnswer(invocacion -> {
            Equipo guardado = invocacion.getArgument(0);
            if (guardado.getId() == null) guardado.setId(1L);
            return guardado;
        });
        Equipo creado = servicio.crear(new EquipoRequest("  Laptop  ", "  Para clases  "));
        assertEquals(1L, creado.getId());
        assertEquals("EQUIPO-001", creado.getCodigo());
        assertEquals("Laptop", creado.getNombre());
        assertEquals("Para clases", creado.getDescripcion());
        assertEquals(true, creado.getActivo());
        assertEquals(true, creado.getDisponible());
    }

    @Test
    void validaNombreYLongitudesAntesDeGuardar() {
        for (EquipoRequest datos : new EquipoRequest[]{null, new EquipoRequest(null, null),
                new EquipoRequest("   ", null), new EquipoRequest("a".repeat(51), null),
                new EquipoRequest("Laptop", "a".repeat(201))}) {
            assertEquals(HttpStatus.BAD_REQUEST, assertThrows(ResponseStatusException.class,
                    () -> servicio.crear(datos)).getStatusCode());
        }
        verify(repository, never()).save(any());
    }

    @Test
    void admiteLimitesYDescripcionOpcional() {
        when(repository.save(any())).thenAnswer(invocacion -> {
            Equipo guardado = invocacion.getArgument(0);
            if (guardado.getId() == null) guardado.setId(2L);
            return guardado;
        });
        assertEquals(200, servicio.crear(new EquipoRequest("a".repeat(50), "b".repeat(200))).getDescripcion().length());
        Equipo sinDesc = servicio.crear(new EquipoRequest("Laptop", null));
        assertNull(sinDesc.getDescripcion());
        assertEquals("EQUIPO-002", sinDesc.getCodigo());
    }

    @Test
    void editarEquipoAntiguoSinCodigoLeAsignaUnoAutomatico() {
        Equipo sinCodigo = new Equipo(5L, null, "Proyector", null, true, true);
        when(repository.findEquipoById(5L)).thenReturn(Optional.of(sinCodigo));
        when(repository.save(any())).thenAnswer(invocacion -> invocacion.getArgument(0));
        Equipo editado = servicio.editar(5L, new EquipoRequest("Proyector Epson", "Aula 3"));
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
        Equipo editado = servicio.editar(1L, new EquipoRequest("Nuevo nombre", null));
        assertEquals(false, editado.getDisponible());
        assertEquals(true, editado.getActivo());
        assertEquals(1L, editado.getId());
        assertEquals("EQUIPO-001", editado.getCodigo());
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
    void inexistenteOInactivoDevuelve404() {
        when(repository.findById(1L)).thenReturn(Optional.of(equipo(false, true)));
        when(repository.findEquipoById(1L)).thenReturn(Optional.of(equipo(false, true)));
        assertEquals(HttpStatus.NOT_FOUND, assertThrows(ResponseStatusException.class, () -> servicio.detalle(1L)).getStatusCode());
        assertEquals(HttpStatus.NOT_FOUND, assertThrows(ResponseStatusException.class,
                () -> servicio.editar(1L, new EquipoRequest("Laptop", null))).getStatusCode());
        assertEquals(HttpStatus.NOT_FOUND, assertThrows(ResponseStatusException.class, () -> servicio.desactivar(9L)).getStatusCode());
    }

    private Equipo equipo(boolean activo, boolean disponible) {
        return new Equipo(1L, "EQUIPO-001", "Laptop", null, disponible, activo);
    }
}
