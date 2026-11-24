package com.soa.proyecto.service;

import com.soa.proyecto.dto.UsuarioResumen;
import com.soa.proyecto.model.Rol;
import com.soa.proyecto.model.Usuario;
import com.soa.proyecto.repository.UsuarioRepository;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class UsuarioServiceTest {
    private final UsuarioRepository repository = mock(UsuarioRepository.class);
    private final UsuarioService servicio = new UsuarioService(repository, new BCryptPasswordEncoder());

    @Test
    void listarOrdenaPorNombreYNuncaExponeLaContrasena() {
        Usuario beto = new Usuario(2L, "beto", "beto", "hash-secreto", Rol.USUARIO);
        Usuario ana = new Usuario(3L, "Ana", "ana", "hash-secreto", Rol.USUARIO);
        beto.setBloqueado(true);
        when(repository.findAll()).thenReturn(List.of(beto, ana));

        List<UsuarioResumen> lista = servicio.listar();

        assertEquals(List.of("Ana", "beto"), lista.stream().map(UsuarioResumen::nombre).toList());
        assertFalse(lista.get(0).bloqueado());
        assertTrue(lista.get(1).bloqueado());
        assertFalse(lista.toString().contains("hash-secreto"));
    }

    @Test
    void bloquearYDesbloquearCambianElIndicador() {
        Usuario usuario = new Usuario(2L, "Ana", "ana", "hash", Rol.USUARIO);
        when(repository.findById(2L)).thenReturn(Optional.of(usuario));
        when(repository.save(any())).thenAnswer(invocacion -> invocacion.getArgument(0));

        assertTrue(servicio.bloquear(2L).bloqueado());
        assertTrue(usuario.getBloqueado());
        assertTrue(servicio.bloquear(2L).bloqueado());
        assertFalse(servicio.desbloquear(2L).bloqueado());
        assertFalse(usuario.getBloqueado());
    }

    @Test
    void noSeBloqueaUnaCuentaDeAdministrador() {
        Usuario admin = new Usuario(1L, "Administrador", "admin", "hash", Rol.ADMIN);
        when(repository.findById(1L)).thenReturn(Optional.of(admin));

        assertEquals(HttpStatus.BAD_REQUEST, assertThrows(ResponseStatusException.class,
                () -> servicio.bloquear(1L)).getStatusCode());
        assertFalse(Boolean.TRUE.equals(admin.getBloqueado()));
        verify(repository, never()).save(any());
    }

    @Test
    void usuarioInexistenteDevuelve404() {
        assertEquals(HttpStatus.NOT_FOUND, assertThrows(ResponseStatusException.class,
                () -> servicio.bloquear(9L)).getStatusCode());
        assertEquals(HttpStatus.NOT_FOUND, assertThrows(ResponseStatusException.class,
                () -> servicio.desbloquear(9L)).getStatusCode());
    }

    @Test
    void crearPorAdminGuardaLaContrasenaCifradaYElRolElegido() {
        when(repository.save(any())).thenAnswer(invocacion -> invocacion.getArgument(0));

        UsuarioResumen creado = servicio.crearPorAdmin("  Ana Pérez ", " ana ", "clave-segura", "admin");

        assertEquals("Ana Pérez", creado.nombre());
        assertEquals("ana", creado.username());
        assertEquals(Rol.ADMIN, creado.rol());
        assertFalse(creado.bloqueado());
        org.mockito.ArgumentCaptor<Usuario> guardado = org.mockito.ArgumentCaptor.forClass(Usuario.class);
        verify(repository).save(guardado.capture());
        assertNotEquals("clave-segura", guardado.getValue().getPassword());
        assertTrue(new BCryptPasswordEncoder().matches("clave-segura", guardado.getValue().getPassword()));
    }

    @Test
    void sinRolSeCreaUnUsuarioComun() {
        when(repository.save(any())).thenAnswer(invocacion -> invocacion.getArgument(0));
        assertEquals(Rol.USUARIO, servicio.crearPorAdmin("Ana", "ana", "clave-segura", null).rol());
        assertEquals(Rol.USUARIO, servicio.crearPorAdmin("Beto", "beto", "clave-segura", "  ").rol());
    }

    @Test
    void crearValidaLosDatosComoElRegistroPublico() {
        String[][] invalidos = {
                {null, "ana", "clave-segura"}, {"Ana", "  ", "clave-segura"}, {"Ana", "ana", null}, {"Ana", "ana", "   "},
                {"a".repeat(51), "ana", "clave-segura"},
                {"Ana", "a".repeat(31), "clave-segura"}, {"Ana", "con espacio", "clave-segura"},
                {"Ana", "ana", "corta"},
                {"Ana", "ana", "ñ".repeat(37)}
        };
        for (String[] d : invalidos) {
            assertEquals(HttpStatus.BAD_REQUEST, assertThrows(ResponseStatusException.class,
                    () -> servicio.crearPorAdmin(d[0], d[1], d[2], null)).getStatusCode());
        }
        assertEquals(HttpStatus.BAD_REQUEST, assertThrows(ResponseStatusException.class,
                () -> servicio.crearPorAdmin("Ana", "ana", "clave-segura", "SUPERADMIN")).getStatusCode());
        verify(repository, never()).save(any());
    }

    @Test
    void unUsuarioRepetidoDevuelve409() {
        when(repository.existsByUsername("ana")).thenReturn(true);
        assertEquals(HttpStatus.CONFLICT, assertThrows(ResponseStatusException.class,
                () -> servicio.crearPorAdmin("Ana", "ana", "clave-segura", null)).getStatusCode());
        verify(repository, never()).save(any());
    }

    @Test
    void unAltaSimultaneaConElMismoUsuarioDevuelve409() {
        when(repository.existsByUsername("ana")).thenReturn(false, true);
        when(repository.save(any())).thenThrow(new DataIntegrityViolationException("duplicado"));
        assertEquals(HttpStatus.CONFLICT, assertThrows(ResponseStatusException.class,
                () -> servicio.crearPorAdmin("Ana", "ana", "clave-segura", null)).getStatusCode());
    }
}
