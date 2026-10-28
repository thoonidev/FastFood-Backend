package com.fastfood.comprobante.config;
import com.fastfood.comprobante.exception.GlobalExceptionHandler;
import com.fastfood.comprobante.security.JwtFilter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpMethod;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
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
    SecurityFilterChain securityFilterChain(HttpSecurity http, JsonMapper mapper,
            @Value("${fastfood.internal.secret}") String secret) throws Exception {
        return http.csrf(csrf -> csrf.disable())
                .httpBasic(basic -> basic.disable()).formLogin(form -> form.disable())
                .logout(logout -> logout.disable()).requestCache(cache -> cache.disable())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .exceptionHandling(handler -> handler
                    .authenticationEntryPoint((req, res, ex) -> responder(req, res, mapper, 401, "AUTENTICACION_REQUERIDA"))
                    .accessDeniedHandler((req, res, ex) -> responder(req, res, mapper, 403, "ACCESO_DENEGADO")))
                .authorizeHttpRequests(auth -> auth
                    .dispatcherTypeMatchers(DispatcherType.ERROR).permitAll()
                    
                    .requestMatchers(HttpMethod.POST, "/ws").hasAuthority("SCOPE_COMPROBANTE_OPERAR")
                    .requestMatchers(HttpMethod.GET, "/ws/comprobantes.wsdl").permitAll()
                    .requestMatchers(HttpMethod.GET, "/actuator/health", "/actuator/info", "/actuator/metrics", "/actuator/metrics/**")
                        .hasAuthority("SCOPE_OBSERVABILIDAD")
                    .anyRequest().denyAll())
                .addFilterBefore(new JwtFilter(secret, mapper), UsernamePasswordAuthenticationFilter.class)
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
