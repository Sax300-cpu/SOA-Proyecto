package com.soa.proyecto.security;

import com.soa.proyecto.model.Usuario;
import com.soa.proyecto.repository.UsuarioRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

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
