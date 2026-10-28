package com.fastfood.socket.config;
import com.fastfood.socket.exception.GlobalExceptionHandler;
import com.fastfood.socket.security.*;
import com.fastfood.socket.feign.AuthEstadoFeignClient;
import org.springframework.http.HttpMethod;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import com.fastfood.socket.websocket.FastFoodWebSocketServer;
import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.HealthIndicator;
import java.io.IOException;
import jakarta.servlet.http.*;
import tools.jackson.databind.json.JsonMapper;
import jakarta.servlet.DispatcherType;
import org.springframework.context.annotation.*;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
public class SecurityConfig {
    @Bean
    HealthIndicator socketHealth(FastFoodWebSocketServer socket) {
        return () -> socket.estaListo() ? Health.up().build() : Health.down().build();
    }
    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http, JsonMapper mapper, JwtUtil jwt, AuthEstadoFeignClient authEstado) throws Exception {
        return http.csrf(csrf -> csrf.disable())
                .httpBasic(basic -> basic.disable()).formLogin(form -> form.disable())
                .logout(logout -> logout.disable()).requestCache(cache -> cache.disable())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .exceptionHandling(handler -> handler
                    .authenticationEntryPoint((req, res, ex) -> responder(req, res, mapper, 401, "AUTENTICACION_REQUERIDA"))
                    .accessDeniedHandler((req, res, ex) -> responder(req, res, mapper, 403, "ACCESO_DENEGADO")))
                .authorizeHttpRequests(auth -> auth
                    .dispatcherTypeMatchers(DispatcherType.ERROR).permitAll()
                    
                    .requestMatchers(HttpMethod.POST, "/internal/socket/eventos").hasAuthority("SCOPE_EVENTO_PUBLICAR")
                    .requestMatchers("/actuator/health", "/actuator/info", "/actuator/metrics", "/actuator/metrics/**").hasRole("ADMINISTRADOR")
                    .anyRequest().denyAll())
                .addFilterBefore(new JwtFilter(jwt, authEstado, mapper), UsernamePasswordAuthenticationFilter.class)
                .build();
    }
    private static void responder(HttpServletRequest request, HttpServletResponse response,
            JsonMapper mapper, int estado, String codigo) throws IOException {
        response.setStatus(estado);
        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");
        mapper.writeValue(response.getWriter(),
                GlobalExceptionHandler.errorBody(estado, codigo, request.getRequestURI()));
    }
}
