package com.soa.proyecto.security;

import com.soa.proyecto.model.Rol;
import com.soa.proyecto.model.Usuario;
import com.soa.proyecto.repository.EquipoRepository;
import com.soa.proyecto.repository.UsuarioRepository;
import com.soa.proyecto.service.EquipoService;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

/**
 * Crea las cuentas de demostración la primera vez que se ejecuta la aplicación
 * y asigna código automático a equipos antiguos que aún no tengan uno.
 */
@Component
@RequiredArgsConstructor
public class DatosIniciales implements CommandLineRunner {

    private final UsuarioRepository usuarioRepository;
    private final EquipoRepository equipoRepository;
    private final PasswordEncoder passwordEncoder;

    @Override
    public void run(String... args) {
        crearSiNoExiste("admin", "Administrador", "admin123", Rol.ADMIN);
        crearSiNoExiste("usuario", "Usuario Demo", "usuario123", Rol.USUARIO);
        completarCodigosEquipos();
    }

    private void completarCodigosEquipos() {
        equipoRepository.findAll().forEach(equipo -> {
            if (equipo.getCodigo() == null || equipo.getCodigo().isBlank()) {
                equipo.setCodigo(EquipoService.generarCodigo(equipo.getId()));
                equipoRepository.save(equipo);
            }
        });
    }

    private void crearSiNoExiste(String username, String nombre, String password, Rol rol) {
        if (!usuarioRepository.existsByUsername(username)) {
            Usuario usuario = new Usuario();
            usuario.setUsername(username);
            usuario.setNombre(nombre);
            usuario.setPassword(passwordEncoder.encode(password));
            usuario.setRol(rol);
            usuarioRepository.save(usuario);
        }
    }
}
