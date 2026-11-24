package com.soa.proyecto.controller;

import com.soa.proyecto.dto.DevolucionPeticion;
import com.soa.proyecto.dto.EntregaPeticion;
import com.soa.proyecto.dto.IncidenciaPeticion;
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
        error(HttpStatus.FORBIDDEN, () -> controller.entregar(1L, null, usuario));
        error(HttpStatus.FORBIDDEN, () -> controller.devolver(1L, null, usuario));
        error(HttpStatus.FORBIDDEN, () -> controller.historial(null, usuario));
        verifyNoInteractions(servicio);
    }

    @Test
    void exigeSesionAutenticadaEnTodasLasOperaciones() {
        error(HttpStatus.UNAUTHORIZED, () -> controller.entregar(1L, null, null));
        error(HttpStatus.UNAUTHORIZED, () -> controller.devolver(1L, null, null));
        error(HttpStatus.UNAUTHORIZED, () -> controller.historial(null, null));
        error(HttpStatus.UNAUTHORIZED, () -> controller.historialPropio(null, null));
        Authentication anonimo = new AnonymousAuthenticationToken("clave", "anonymousUser",
                List.of(new SimpleGrantedAuthority("ROLE_USUARIO")));
        error(HttpStatus.UNAUTHORIZED, () -> controller.historialPropio(null, anonimo));
        Authentication sinAutenticar = new UsernamePasswordAuthenticationToken("emilio", "");
        error(HttpStatus.UNAUTHORIZED, () -> controller.entregar(1L, null, sinAutenticar));
        verifyNoInteractions(servicio);
    }

    @Test
    void adminPuedeOperarYSinCuerpoLaObservacionEsNula() {
        controller.entregar(1L, null, sesion("ADMIN"));
        controller.devolver(1L, null, sesion("ADMIN"));
        controller.historial(null, sesion("ADMIN"));
        verify(servicio).entregar(1L, null, null);
        verify(servicio).devolver(1L, null, null);
        verify(servicio).historial(null);
    }

    @Test
    void devuelveConObservacionYPropioNoRecibeIdDelCliente() {
        controller.devolver(1L, new DevolucionPeticion("Rayón", "CON_DETALLES"), sesion("ADMIN"));
        controller.historialPropio(null, sesion("USUARIO"));
        verify(servicio).devolver(1L, "Rayón", "CON_DETALLES");
        verify(servicio).historialPropio("emilio", null);
    }

    @Test
    void entregaLlevaLaObservacionYSoloElAdminAtiendeIncidencias() {
        controller.entregar(1L, new EntregaPeticion("Con un rayón"), sesion("ADMIN"));
        controller.entregar(2L, new EntregaPeticion(null, 2), sesion("ADMIN"));
        verify(servicio).entregar(2L, null, 2);
        verify(servicio).entregar(1L, "Con un rayón", null);

        controller.atenderIncidencia(1L, new IncidenciaPeticion("REPARADO"), sesion("ADMIN"));
        controller.atenderIncidencia(2L, null, sesion("ADMIN"));
        verify(servicio).atenderIncidencia(1L, "REPARADO");
        verify(servicio).atenderIncidencia(2L, null);

        Authentication usuario = sesion("USUARIO");
        error(HttpStatus.FORBIDDEN, () -> controller.atenderIncidencia(1L, null, usuario));
        error(HttpStatus.UNAUTHORIZED, () -> controller.atenderIncidencia(1L, null, null));
    }

    private Authentication sesion(String rol) {
        return new UsernamePasswordAuthenticationToken("emilio", "", List.of(new SimpleGrantedAuthority("ROLE_" + rol)));
    }

    private void error(HttpStatus estado, org.junit.jupiter.api.function.Executable accion) {
        assertEquals(estado, assertThrows(ResponseStatusException.class, accion).getStatusCode());
    }
}
