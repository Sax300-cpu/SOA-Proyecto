package com.soa.proyecto.security;

import com.soa.proyecto.repository.UsuarioRepository;
import lombok.RequiredArgsConstructor;
import tools.jackson.databind.ObjectMapper;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.csrf.CsrfTokenRequestAttributeHandler;

import java.util.Map;

// Configura Spring Security: permisos por rol, CSRF, login/logout y respuestas JSON.
@Configuration
@EnableWebSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final ObjectMapper objectMapper;
    private final UsuarioRepository usuarioRepository;

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                // CSRF con cookie para que el cliente JavaScript pueda leerlo y devolverlo en el encabezado.
                .csrf(csrf -> csrf
                        .csrfTokenRepository(CookieCsrfTokenRepository.withHttpOnlyFalse())
                        .csrfTokenRequestHandler(new CsrfTokenRequestAttributeHandler())
                )
                .authorizeHttpRequests(auth -> auth
                        // Recursos públicos: interfaz estática y endpoints de autenticación.
                        .requestMatchers("/", "/**/*.html", "/favicon.ico",
                                "/css/**", "/js/**", "/img/**",
                                "/api/auth/**").permitAll()
                        // Equipos: consulta para ambos roles; escritura solo ADMIN.
                        .requestMatchers(HttpMethod.GET, "/api/equipos", "/api/equipos/*").hasAnyRole("ADMIN", "USUARIO")
                        .requestMatchers("/api/equipos/**").hasRole("ADMIN")
                        // Préstamos: solicitar y ver los propios como USUARIO.
                        .requestMatchers(HttpMethod.POST, "/api/prestamos").hasRole("USUARIO")
                        .requestMatchers(HttpMethod.GET, "/api/prestamos/mios").hasRole("USUARIO")
                        // El resto de préstamos (pendientes, aprobar, entregar, devolver, historial): ADMIN.
                        .requestMatchers("/api/prestamos/**").hasRole("ADMIN")
                        .anyRequest().authenticated()
                )
                .formLogin(form -> form
                        .loginProcessingUrl("/api/auth/login")
                        .successHandler((request, response, auth) -> {
                            response.setStatus(200);
                            response.setContentType("application/json");
                            response.setCharacterEncoding("UTF-8");
                            objectMapper.writeValue(response.getWriter(),
                                    Map.of("username", auth.getName(), "rol", rolDe(auth)));
                        })
                        .failureHandler((request, response, exception) -> {
                            // Distingue campos vacíos, usuario inexistente o contraseña incorrecta.
                            String username = request.getParameter("username");
                            String password = request.getParameter("password");
                            String mensajeError;
                            if (username == null || username.isBlank() || password == null || password.isBlank()) {
                                mensajeError = "Debes ingresar el usuario y la contraseña.";
                            } else if (!usuarioRepository.existsByUsername(username.strip())) {
                                mensajeError = "El usuario ingresado no existe.";
                            } else {
                                mensajeError = "La contraseña ingresada es incorrecta.";
                            }
                            response.setStatus(401);
                            response.setContentType("application/json");
                            response.setCharacterEncoding("UTF-8");
                            objectMapper.writeValue(response.getWriter(), Map.of("error", mensajeError));
                        })
                )
                .logout(logout -> logout
                        .logoutUrl("/api/auth/logout")
                        .logoutSuccessHandler((request, response, auth) -> {
                            response.setStatus(200);
                            response.setContentType("application/json");
                            response.setCharacterEncoding("UTF-8");
                            objectMapper.writeValue(response.getWriter(), Map.of("mensaje", "Sesión cerrada"));
                        })
                )
                .exceptionHandling(ex -> ex
                        .authenticationEntryPoint((request, response, exception) -> {
                            response.setStatus(401);
                            response.setContentType("application/json");
                            response.setCharacterEncoding("UTF-8");
                            objectMapper.writeValue(response.getWriter(), Map.of("error", "No autenticado"));
                        })
                        .accessDeniedHandler((request, response, exception) -> {
                            response.setStatus(403);
                            response.setContentType("application/json");
                            response.setCharacterEncoding("UTF-8");
                            objectMapper.writeValue(response.getWriter(), Map.of("error", "Sin permisos"));
                        })
                );

        return http.build();
    }

    // Quita el prefijo "ROLE_" de Spring Security para devolver ADMIN o USUARIO.
    private String rolDe(Authentication auth) {
        for (GrantedAuthority permiso : auth.getAuthorities()) {
            String nombre = permiso.getAuthority();
            if (nombre != null && nombre.startsWith("ROLE_")) {
                return nombre.substring("ROLE_".length());
            }
        }
        return "";
    }
}
