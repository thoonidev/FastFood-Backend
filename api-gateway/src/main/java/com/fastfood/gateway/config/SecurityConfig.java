package com.fastfood.gateway.config;

import com.fastfood.gateway.security.*;

import jakarta.servlet.DispatcherType;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.*;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.client.RestClient;

import tools.jackson.databind.json.JsonMapper;

@Configuration
public class SecurityConfig {

    @Bean
    SecurityFilterChain securityFilterChain(
            HttpSecurity http,
            JwtUtil jwt,
            JsonMapper mapper,
            @Qualifier("authEstadoClient") RestClient authClient)
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
                                        .requestMatchers("/internal/**", "/ws", "/ws/**")
                                        .denyAll()
                                        .requestMatchers(
                                                "/actuator/health",
                                                "/actuator/info",
                                                "/actuator/metrics",
                                                "/actuator/metrics/**")
                                        .hasRole("ADMINISTRADOR")
                                        .requestMatchers(
                                                HttpMethod.POST,
                                                "/api/auth/login",
                                                "/api/auth/register")
                                        .permitAll()
                                        .requestMatchers(
                                                HttpMethod.GET,
                                                "/api/categorias",
                                                "/api/productos",
                                                "/api/productos/{id:[0-9]+}")
                                        .permitAll()
                                        .requestMatchers(
                                                HttpMethod.OPTIONS,
                                                "/api/auth/**",
                                                "/api/usuarios/**",
                                                "/api/empleados/**",
                                                "/api/productos/**",
                                                "/api/categorias/**",
                                                "/api/admin/productos/**",
                                                "/api/admin/categorias/**",
                                                "/api/cotizaciones/**",
                                                "/api/pedidos/**",
                                                "/api/conversaciones/**",
                                                "/api/pagos/configuracion",
                                                "/api/admin/dashboard",
                                                "/api/admin/reportes/**")
                                        .permitAll()
                                        .requestMatchers(
                                                "/api/auth/**",
                                                "/api/usuarios/**",
                                                "/api/empleados/**",
                                                "/api/productos/**",
                                                "/api/categorias/**",
                                                "/api/admin/productos/**",
                                                "/api/admin/categorias/**",
                                                "/api/cotizaciones/**",
                                                "/api/pedidos/**",
                                                "/api/conversaciones/**",
                                                "/api/pagos/configuracion",
                                                "/api/admin/dashboard",
                                                "/api/admin/reportes/**")
                                        .authenticated()
                                        .anyRequest()
                                        .denyAll())
                .addFilterBefore(
                        new JwtFilter(jwt, mapper, authClient),
                        UsernamePasswordAuthenticationFilter.class)
                .build();
    }
}
