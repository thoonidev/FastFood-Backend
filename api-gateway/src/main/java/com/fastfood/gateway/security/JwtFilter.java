package com.fastfood.gateway.security;

import com.fastfood.gateway.exception.GlobalExceptionHandler;

import io.jsonwebtoken.JwtException;

import jakarta.servlet.*;
import jakarta.servlet.http.*;

import org.springframework.core.ParameterizedTypeReference;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.client.*;
import org.springframework.web.filter.OncePerRequestFilter;

import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.util.List;
import java.util.Map;

public class JwtFilter extends OncePerRequestFilter {
    private final JwtUtil jwt;
    private final JsonMapper mapper;
    private final RestClient authClient;

    public JwtFilter(JwtUtil jwt, JsonMapper mapper, RestClient authClient) {
        this.jwt = jwt;
        this.mapper = mapper;
        this.authClient = authClient;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String ruta = request.getRequestURI();
        return (!ruta.startsWith("/api/") && !ruta.startsWith("/actuator/"))
                || "OPTIONS".equals(request.getMethod())
                || ("POST".equals(request.getMethod())
                        && (ruta.equals("/api/auth/login") || ruta.equals("/api/auth/register")))
                || ("GET".equals(request.getMethod())
                        && (ruta.equals("/api/categorias")
                                || ruta.equals("/api/productos")
                                || ruta.matches("/api/productos/[0-9]+")));
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String header = request.getHeader("Authorization");
        if (header != null) {
            try {
                if (!header.startsWith("Bearer ") || header.length() <= 7)
                    throw new JwtException("Bearer invalido");
                var claims = jwt.validar(header.substring(7));
                if (request.getRequestURI().startsWith("/actuator/")
                        && "ADMINISTRADOR".equals(claims.get("rol", String.class))) {
                    Map<String, Object> usuario =
                            authClient
                                    .get()
                                    .uri("/api/auth/me")
                                    .header("Authorization", header)
                                    .retrieve()
                                    .body(new ParameterizedTypeReference<Map<String, Object>>() {});
                    if (usuario == null
                            || !"ACTIVO".equals(usuario.get("estado"))
                            || !"ADMINISTRADOR".equals(usuario.get("rol"))
                            || !(usuario.get("id") instanceof Number id)
                            || id.longValue() != ((Number) claims.get("usuarioId")).longValue()
                            || !(usuario.get("versionSeguridad") instanceof Number version)
                            || version.longValue()
                                    != ((Number) claims.get("versionSeguridad")).longValue()) {
                        responder(request, response, mapper, 401, "SESION_VENCIDA");
                        return;
                    }
                }
                var auth =
                        new UsernamePasswordAuthenticationToken(
                                claims,
                                null,
                                List.of(
                                        new SimpleGrantedAuthority(
                                                "ROLE_" + claims.get("rol", String.class))));
                SecurityContextHolder.getContext().setAuthentication(auth);
            } catch (JwtException | IllegalArgumentException ex) {
                SecurityContextHolder.clearContext();
                responder(
                        request,
                        response,
                        mapper,
                        401,
                        ex instanceof io.jsonwebtoken.ExpiredJwtException
                                ? "SESION_VENCIDA"
                                : "TOKEN_INVALIDO");
                return;
            } catch (HttpClientErrorException ex) {
                SecurityContextHolder.clearContext();
                int status =
                        ex.getStatusCode().value() == 401 || ex.getStatusCode().value() == 403
                                ? 401
                                : 503;
                responder(
                        request,
                        response,
                        mapper,
                        status,
                        status == 401 ? "SESION_VENCIDA" : "DEPENDENCIA_NO_DISPONIBLE");
                return;
            } catch (RestClientException ex) {
                SecurityContextHolder.clearContext();
                responder(request, response, mapper, 503, "DEPENDENCIA_NO_DISPONIBLE");
                return;
            }
        }
        chain.doFilter(request, response);
    }

    public static void responder(
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
