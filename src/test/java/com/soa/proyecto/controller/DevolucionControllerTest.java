package com.soa.proyecto.controller;

import com.soa.proyecto.dto.DevolucionPeticion;
import com.soa.proyecto.service.DevolucionService;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class DevolucionControllerTest {
    private final DevolucionService servicio = mock(DevolucionService.class);
    private final DevolucionController controller = new DevolucionController(servicio);

    @Test
    void usuarioNoPuedeEntregarDevolverNiVerHistorialAjeno() {
        Authentication usuario = sesion("USUARIO");
        error(HttpStatus.FORBIDDEN, () -> controller.entregar(1L, usuario));
        error(HttpStatus.FORBIDDEN, () -> controller.devolver(1L, null, usuario));
        error(HttpStatus.FORBIDDEN, () -> controller.historial(null, usuario));
        verifyNoInteractions(servicio);
    }

    @Test
    void exigeSesionAutenticadaEnTodasLasOperaciones() {
        error(HttpStatus.UNAUTHORIZED, () -> controller.entregar(1L, null));
        error(HttpStatus.UNAUTHORIZED, () -> controller.devolver(1L, null, null));
        error(HttpStatus.UNAUTHORIZED, () -> controller.historial(null, null));
        error(HttpStatus.UNAUTHORIZED, () -> controller.historialPropio(null, null));
        Authentication anonimo = new AnonymousAuthenticationToken("clave", "anonymousUser",
                List.of(new SimpleGrantedAuthority("ROLE_USUARIO")));
        error(HttpStatus.UNAUTHORIZED, () -> controller.historialPropio(null, anonimo));
        Authentication sinAutenticar = new UsernamePasswordAuthenticationToken("emilio", "");
        error(HttpStatus.UNAUTHORIZED, () -> controller.entregar(1L, sinAutenticar));
        verifyNoInteractions(servicio);
    }

    @Test
    void adminPuedeOperarYSinCuerpoLaObservacionEsNula() {
        controller.entregar(1L, sesion("ADMIN"));
        controller.devolver(1L, null, sesion("ADMIN"));
        controller.historial(null, sesion("ADMIN"));
        verify(servicio).entregar(1L);
        verify(servicio).devolver(1L, null);
        verify(servicio).historial(null);
    }

    @Test
    void devuelveConObservacionYPropioNoRecibeIdDelCliente() {
        controller.devolver(1L, new DevolucionPeticion("Rayón"), sesion("ADMIN"));
        controller.historialPropio(null, sesion("USUARIO"));
        verify(servicio).devolver(1L, "Rayón");
        verify(servicio).historialPropio("emilio", null);
    }

    private Authentication sesion(String rol) {
        return new UsernamePasswordAuthenticationToken("emilio", "", List.of(new SimpleGrantedAuthority("ROLE_" + rol)));
    }

    private void error(HttpStatus estado, org.junit.jupiter.api.function.Executable accion) {
        assertEquals(estado, assertThrows(ResponseStatusException.class, accion).getStatusCode());
    }
}
