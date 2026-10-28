package com.fastfood.auth.config;

import com.fastfood.auth.exception.GlobalExceptionHandler;
import com.fastfood.auth.repository.UsuarioRepository;
import com.fastfood.auth.security.*;

import jakarta.servlet.DispatcherType;
import jakarta.servlet.http.*;

import org.springframework.context.annotation.*;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;

@Configuration
public class SecurityConfig {

    @Bean
    PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    SecurityFilterChain securityFilterChain(
            HttpSecurity http, JsonMapper mapper, JwtUtil jwt, UsuarioRepository usuarios)
            throws Exception {
        return http.csrf(csrf -> csrf.disable())
                .httpBasic(basic -> basic.disable())
                .formLogin(form -> form.disable())
                .logout(logout -> logout.disable())
                .requestCache(cache -> cache.disable())
                .sessionManagement(
                        session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .exceptionHandling(
                        handler ->
                                handler.authenticationEntryPoint(
                                                (req, res, ex) ->
                                                        responder(
                                                                req,
                                                                res,
                                                                mapper,
                                                                401,
                                                                "AUTENTICACION_REQUERIDA"))
                                        .accessDeniedHandler(
                                                (req, res, ex) ->
                                                        responder(
                                                                req,
                                                                res,
                                                                mapper,
                                                                403,
                                                                "ACCESO_DENEGADO")))
                .authorizeHttpRequests(
                        auth ->
                                auth.dispatcherTypeMatchers(DispatcherType.ERROR)
                                        .permitAll()
                                        .requestMatchers(
                                                org.springframework.http.HttpMethod.POST,
                                                "/api/auth/login",
                                                "/api/auth/register")
                                        .permitAll()
                                        .requestMatchers(
                                                org.springframework.http.HttpMethod.GET,
                                                "/internal/auth/usuarios/{id:[0-9]+}/estado")
                                        .hasAuthority("SCOPE_USUARIO_ESTADO")
                                        .requestMatchers("/internal/**")
                                        .denyAll()
                                        .requestMatchers(
                                                "/actuator/health",
                                                "/actuator/info",
                                                "/actuator/metrics",
                                                "/actuator/metrics/**")
                                        .hasRole("ADMINISTRADOR")
                                        .requestMatchers(
                                                org.springframework.http.HttpMethod.GET,
                                                "/api/auth/me")
                                        .authenticated()
                                        .requestMatchers(
                                                org.springframework.http.HttpMethod.PUT,
                                                "/api/usuarios/me")
                                        .authenticated()
                                        .requestMatchers(
                                                org.springframework.http.HttpMethod.GET,
                                                "/api/usuarios",
                                                "/api/empleados")
                                        .hasRole("ADMINISTRADOR")
                                        .requestMatchers(
                                                org.springframework.http.HttpMethod.POST,
                                                "/api/empleados")
                                        .hasRole("ADMINISTRADOR")
                                        .requestMatchers(
                                                org.springframework.http.HttpMethod.PUT,
                                                "/api/empleados/{id:[0-9]+}")
                                        .hasRole("ADMINISTRADOR")
                                        .requestMatchers(
                                                org.springframework.http.HttpMethod.PATCH,
                                                "/api/empleados/{id:[0-9]+}/estado")
                                        .hasRole("ADMINISTRADOR")
                                        .anyRequest()
                                        .denyAll())
                .addFilterBefore(
                        new JwtFilter(jwt, usuarios, mapper),
                        UsernamePasswordAuthenticationFilter.class)
                .build();
    }

    private static void responder(
            HttpServletRequest request,
            HttpServletResponse response,
            JsonMapper mapper,
            int estado,
            String codigo)
            throws IOException {
        response.setStatus(estado);
        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");
        mapper.writeValue(
                response.getWriter(),
                GlobalExceptionHandler.errorBody(estado, codigo, request.getRequestURI()));
    }
}
