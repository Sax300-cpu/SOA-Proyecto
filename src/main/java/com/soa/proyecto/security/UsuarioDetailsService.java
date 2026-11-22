package com.soa.proyecto.security;

import com.soa.proyecto.model.Usuario;
import com.soa.proyecto.repository.UsuarioRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

// Carga el usuario desde la base de datos para que Spring Security valide el login.
@Service
@RequiredArgsConstructor
public class UsuarioDetailsService implements UserDetailsService {

    private final UsuarioRepository usuarioRepository;

    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        String limpio = username == null ? "" : username.strip();
        Usuario usuario = usuarioRepository.findByUsername(limpio)
                .orElseThrow(() -> new UsernameNotFoundException("Usuario no encontrado: " + limpio));

        return org.springframework.security.core.userdetails.User
                .withUsername(usuario.getUsername())
                .password(usuario.getPassword())
                .authorities("ROLE_" + usuario.getRol().name())
                .build();
    }
}
