package com.fastfood.order.config;

import com.fastfood.order.feign.AuthEstadoFeignClient;
import com.fastfood.order.security.*;

import jakarta.servlet.DispatcherType;

import org.springframework.context.annotation.*;
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
                                        .requestMatchers(HttpMethod.POST,
                                                "/internal/chat/pedidos/{id:[0-9]+}/acceso",
                                                "/internal/chat/pedidos/{id:[0-9]+}/mensajes")
                                        .hasAnyRole("CLIENTE", "EMPLEADO", "ADMINISTRADOR")
                                        .requestMatchers(HttpMethod.GET,
                                                "/api/pedidos/{id:[0-9]+}/conversacion",
                                                "/api/pedidos/{id:[0-9]+}/mensajes")
                                        .hasAnyRole("CLIENTE", "EMPLEADO", "ADMINISTRADOR")
                                        .requestMatchers(HttpMethod.GET, "/api/conversaciones")
                                        .hasAnyRole("EMPLEADO", "ADMINISTRADOR")
                                        .requestMatchers("/internal/**")
                                        .denyAll()
                                        .requestMatchers(HttpMethod.GET, "/api/admin/dashboard", "/api/admin/reportes/**")
                                        .hasRole("ADMINISTRADOR")
                                        .requestMatchers(HttpMethod.GET, "/api/pedidos/{id:[0-9]+}/comprobante")
                                        .hasAnyRole("CLIENTE", "EMPLEADO", "ADMINISTRADOR")
                                        .requestMatchers(HttpMethod.POST, "/api/pedidos/{id:[0-9]+}/comprobante")
                                        .hasAnyRole("CLIENTE", "EMPLEADO", "ADMINISTRADOR")
                                        .requestMatchers(
                                                "/actuator/health",
                                                "/actuator/info",
                                                "/actuator/metrics",
                                                "/actuator/metrics/**")
                                        .hasRole("ADMINISTRADOR")
                                        .requestMatchers(HttpMethod.GET, "/api/pagos/configuracion",
                                                "/api/pedidos/{id:[0-9]+}/pago",
                                                "/api/pedidos/{id:[0-9]+}/pago/intentos")
                                        .hasAnyRole("CLIENTE", "EMPLEADO", "ADMINISTRADOR")
                                        .requestMatchers(HttpMethod.POST,
                                                "/api/pedidos/{id:[0-9]+}/pago/yape",
                                                "/api/pedidos/{id:[0-9]+}/pago/tarjeta")
                                        .hasRole("CLIENTE")
                                        .requestMatchers(HttpMethod.POST,
                                                "/api/pedidos/{id:[0-9]+}/pago/yape/validacion",
                                                "/api/pedidos/{id:[0-9]+}/pago/efectivo/confirmacion")
                                        .hasAnyRole("EMPLEADO", "ADMINISTRADOR")
                                        .requestMatchers(
                                                HttpMethod.POST,
                                                "/api/cotizaciones",
                                                "/api/pedidos")
                                        .hasRole("CLIENTE")
                                        .requestMatchers(HttpMethod.GET, "/api/pedidos/mis-pedidos")
                                        .hasRole("CLIENTE")
                                        .requestMatchers(HttpMethod.GET, "/api/pedidos")
                                        .hasAnyRole("EMPLEADO", "ADMINISTRADOR")
                                        .requestMatchers(
                                                HttpMethod.GET,
                                                "/api/pedidos/{id:[0-9]+}",
                                                "/api/pedidos/{id:[0-9]+}/cambios-estado")
                                        .hasAnyRole("CLIENTE", "EMPLEADO", "ADMINISTRADOR")
                                        .requestMatchers(
                                                HttpMethod.PATCH, "/api/pedidos/{id:[0-9]+}/estado")
                                        .hasAnyRole("EMPLEADO", "ADMINISTRADOR")
                                        .requestMatchers(
                                                HttpMethod.POST,
                                                "/api/pedidos/{id:[0-9]+}/cancelacion")
                                        .hasAnyRole("EMPLEADO", "ADMINISTRADOR")
                                        .anyRequest()
                                        .denyAll())
                .addFilterBefore(
                        new JwtFilter(jwt, authEstado, mapper),
                        UsernamePasswordAuthenticationFilter.class)
                .build();
    }
}
