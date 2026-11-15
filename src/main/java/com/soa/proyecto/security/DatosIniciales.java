package com.soa.proyecto.security;

import com.soa.proyecto.model.Rol;
import com.soa.proyecto.model.Usuario;
import com.soa.proyecto.repository.UsuarioRepository;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

/**
 * Crea las cuentas de demostración la primera vez que se ejecuta la aplicación.
 * Las contraseñas se guardan encriptadas; solo son para desarrollo.
 */
@Component
public class DatosIniciales implements CommandLineRunner {

    private final UsuarioRepository usuarioRepository;
    private final PasswordEncoder passwordEncoder;

    public DatosIniciales(UsuarioRepository usuarioRepository, PasswordEncoder passwordEncoder) {
        this.usuarioRepository = usuarioRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    public void run(String... args) {
        crearSiNoExiste("admin", "Administrador", "admin123", Rol.ADMIN);
        crearSiNoExiste("usuario", "Usuario Demo", "usuario123", Rol.USUARIO);
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
