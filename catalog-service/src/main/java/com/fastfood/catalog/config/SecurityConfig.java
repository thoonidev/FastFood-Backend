package com.fastfood.catalog.config;

import com.fastfood.catalog.feign.AuthEstadoFeignClient;
import com.fastfood.catalog.security.JwtFilter;
import com.fastfood.catalog.security.JwtUtil;

import jakarta.servlet.DispatcherType;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

import tools.jackson.databind.json.JsonMapper;

@Configuration
public class SecurityConfig {

    @Bean
    SecurityFilterChain securityFilterChain(
            HttpSecurity http, JsonMapper mapper, JwtUtil jwt, AuthEstadoFeignClient authEstado)
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
                                                        JwtFilter.responder(
                                                                req,
                                                                res,
                                                                mapper,
                                                                401,
                                                                "AUTENTICACION_REQUERIDA"))
                                        .accessDeniedHandler(
                                                (req, res, ex) ->
                                                        JwtFilter.responder(
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
                                                HttpMethod.GET,
                                                "/api/categorias",
                                                "/api/productos",
                                                "/api/productos/{id:[0-9]+}")
                                        .permitAll()
                                        .requestMatchers(
                                                HttpMethod.POST, "/internal/catalogo/validar")
                                        .hasAuthority("SCOPE_CATALOGO_VALIDAR")
                                        .requestMatchers("/internal/**")
                                        .denyAll()
                                        .requestMatchers(
                                                "/actuator/health",
                                                "/actuator/info",
                                                "/actuator/metrics",
                                                "/actuator/metrics/**")
                                        .hasRole("ADMINISTRADOR")
                                        .requestMatchers(
                                                HttpMethod.GET,
                                                "/api/admin/categorias",
                                                "/api/admin/productos",
                                                "/api/admin/productos/{id:[0-9]+}")
                                        .hasRole("ADMINISTRADOR")
                                        .requestMatchers(
                                                HttpMethod.POST,
                                                "/api/admin/categorias",
                                                "/api/admin/productos")
                                        .hasRole("ADMINISTRADOR")
                                        .requestMatchers(
                                                HttpMethod.PUT,
                                                "/api/admin/categorias/{id:[0-9]+}",
                                                "/api/admin/productos/{id:[0-9]+}")
                                        .hasRole("ADMINISTRADOR")
                                        .requestMatchers(
                                                HttpMethod.PATCH,
                                                "/api/admin/categorias/{id:[0-9]+}/estado",
                                                "/api/admin/productos/{id:[0-9]+}/estado")
                                        .hasRole("ADMINISTRADOR")
                                        .anyRequest()
                                        .denyAll())
                .addFilterBefore(
                        new JwtFilter(jwt, authEstado, mapper),
                        UsernamePasswordAuthenticationFilter.class)
                .build();
    }
}
