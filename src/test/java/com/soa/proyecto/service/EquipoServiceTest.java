package com.soa.proyecto.service;

import com.soa.proyecto.dto.EquipoRequest;
import com.soa.proyecto.model.Equipo;
import com.soa.proyecto.repository.EquipoRepository;
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
    private final EquipoService servicio = new EquipoService(repository);

    @Test
    void crearNormalizaDatosYEstableceDisponibilidad() {
        when(repository.save(any())).thenAnswer(invocacion -> invocacion.getArgument(0));
        Equipo creado = servicio.crear(new EquipoRequest("  lap-001 ", "  Laptop  ", "  Para clases  "));
        assertEquals("LAP-001", creado.getCodigo());
        assertEquals("Laptop", creado.getNombre());
        assertEquals("Para clases", creado.getDescripcion());
        assertEquals(true, creado.getActivo());
        assertEquals(true, creado.getDisponible());
        assertNull(creado.getId());
    }

    @Test
    void validaNombreYLongitudesAntesDeGuardar() {
        for (EquipoRequest datos : new EquipoRequest[]{null, new EquipoRequest("LAP-1", null, null),
                new EquipoRequest("LAP-1", "   ", null), new EquipoRequest("LAP-1", "a".repeat(51), null),
                new EquipoRequest("LAP-1", "Laptop", "a".repeat(201)),
                new EquipoRequest(null, "Laptop", null), new EquipoRequest("  ", "Laptop", null),
                new EquipoRequest("LAP 001", "Laptop", null), new EquipoRequest("KSDKAKDKASKAKD", "Laptop", null),
                new EquipoRequest("LAP001", "Laptop", null), new EquipoRequest("LAP-12345", "Laptop", null),
                new EquipoRequest("L-001", "Laptop", null)}) {
            assertEquals(HttpStatus.BAD_REQUEST, assertThrows(ResponseStatusException.class,
                    () -> servicio.crear(datos)).getStatusCode());
        }
        verify(repository, never()).save(any());
    }

    @Test
    void admiteLimitesYDescripcionOpcional() {
        when(repository.save(any())).thenAnswer(invocacion -> invocacion.getArgument(0));
        assertEquals(200, servicio.crear(new EquipoRequest("ABCDE-9999", "a".repeat(50), "b".repeat(200))).getDescripcion().length());
        assertNull(servicio.crear(new EquipoRequest("LAP-2", "Laptop", null)).getDescripcion());
    }

    @Test
    void codigoRepetidoAlCrearDevuelve409() {
        when(repository.existsByCodigo("LAP-001")).thenReturn(true);
        assertEquals(HttpStatus.CONFLICT, assertThrows(ResponseStatusException.class,
                () -> servicio.crear(new EquipoRequest("lap-001", "Laptop", null))).getStatusCode());
        verify(repository, never()).save(any());
    }

    @Test
    void codigoRepetidoDeOtroEquipoAlEditarDevuelve409() {
        when(repository.findEquipoById(1L)).thenReturn(Optional.of(equipo(true, true)));
        when(repository.existsByCodigoAndIdNot("PRO-001", 1L)).thenReturn(true);
        assertEquals(HttpStatus.CONFLICT, assertThrows(ResponseStatusException.class,
                () -> servicio.editar(1L, new EquipoRequest("PRO-001", "Laptop", null))).getStatusCode());
        verify(repository, never()).save(any());
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
        Equipo editado = servicio.editar(1L, new EquipoRequest("LAP-001", "Nuevo nombre", null));
        assertEquals(false, editado.getDisponible());
        assertEquals(true, editado.getActivo());
        assertEquals(1L, editado.getId());
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
    void inexistenteOInactivoDevuelve404() {
        when(repository.findById(1L)).thenReturn(Optional.of(equipo(false, true)));
        when(repository.findEquipoById(1L)).thenReturn(Optional.of(equipo(false, true)));
        assertEquals(HttpStatus.NOT_FOUND, assertThrows(ResponseStatusException.class, () -> servicio.detalle(1L)).getStatusCode());
        assertEquals(HttpStatus.NOT_FOUND, assertThrows(ResponseStatusException.class,
                () -> servicio.editar(1L, new EquipoRequest("LAP-001", "Laptop", null))).getStatusCode());
        assertEquals(HttpStatus.NOT_FOUND, assertThrows(ResponseStatusException.class, () -> servicio.desactivar(9L)).getStatusCode());
    }

    private Equipo equipo(boolean activo, boolean disponible) {
        return new Equipo(1L, "LAP-001", "Laptop", null, disponible, activo);
    }
}
