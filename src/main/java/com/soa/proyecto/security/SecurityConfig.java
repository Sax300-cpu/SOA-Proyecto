package com.soa.proyecto.security;

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

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    private final ObjectMapper objectMapper;

    public SecurityConfig(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

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
                            objectMapper.writeValue(response.getWriter(),
                                    Map.of("username", auth.getName(), "rol", rolDe(auth)));
                        })
                        .failureHandler((request, response, exception) -> {
                            response.setStatus(401);
                            response.setContentType("application/json");
                            objectMapper.writeValue(response.getWriter(), Map.of("error", "Credenciales inválidas"));
                        })
                )
                .logout(logout -> logout
                        .logoutUrl("/api/auth/logout")
                        .logoutSuccessHandler((request, response, auth) -> {
                            response.setStatus(200);
                            response.setContentType("application/json");
                            objectMapper.writeValue(response.getWriter(), Map.of("mensaje", "Sesión cerrada"));
                        })
                )
                .exceptionHandling(ex -> ex
                        .authenticationEntryPoint((request, response, exception) -> {
                            response.setStatus(401);
                            response.setContentType("application/json");
                            objectMapper.writeValue(response.getWriter(), Map.of("error", "No autenticado"));
                        })
                        .accessDeniedHandler((request, response, exception) -> {
                            response.setStatus(403);
                            response.setContentType("application/json");
                            objectMapper.writeValue(response.getWriter(), Map.of("error", "Sin permisos"));
                        })
                );

        return http.build();
    }

    private String rolDe(Authentication auth) {
        return auth.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .filter(a -> a.startsWith("ROLE_"))
                .map(a -> a.substring("ROLE_".length()))
                .findFirst()
                .orElse("");
    }
}
