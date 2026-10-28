package com.fastfood.socket.security;

import com.fastfood.socket.dto.UsuarioEstadoResponse;
import com.fastfood.socket.exception.GlobalExceptionHandler;
import com.fastfood.socket.feign.AuthEstadoFeignClient;

import feign.FeignException;

import io.jsonwebtoken.*;

import jakarta.servlet.*;
import jakarta.servlet.http.*;

import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.util.List;

public class JwtFilter extends OncePerRequestFilter {

    private final JwtUtil jwt;
    private final AuthEstadoFeignClient auth;
    private final JsonMapper mapper;

    public JwtFilter(JwtUtil jwt, AuthEstadoFeignClient auth, JsonMapper mapper) {
        this.jwt = jwt;
        this.auth = auth;
        this.mapper = mapper;
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String header = request.getHeader("Authorization");
        if (header != null) {
            try {
                if (!header.startsWith("Bearer ") || header.length() <= 7) {
                    throw new JwtException("Bearer invalido");
                }
                Claims claims;
                String autoridad;
                if (request.getRequestURI().startsWith("/internal/")) {
                    claims = jwt.validarTecnico(header.substring(7));
                    if (!"POST".equals(request.getMethod())
                            || !"/internal/socket/eventos".equals(request.getRequestURI())) {
                        responder(request, response, mapper, 403, "ACCESO_DENEGADO");
                        return;
                    }
                    autoridad = "SCOPE_EVENTO_PUBLICAR";
                } else {
                    claims = jwt.validar(header.substring(7));
                    Long id = ((Number) claims.get("usuarioId")).longValue();
                    UsuarioEstadoResponse usuario =
                            auth.obtener(id, "Bearer " + jwt.generarTecnicoAuth());
                    if (usuario == null
                            || usuario.getId() == null
                            || usuario.getRol() == null
                            || usuario.getCorreo() == null
                            || usuario.getEstado() == null
                            || usuario.getVersionSeguridad() == null) {
                        responder(request, response, mapper, 503, "DEPENDENCIA_NO_DISPONIBLE");
                        return;
                    }
                    if (!"ACTIVO".equals(usuario.getEstado())) {
                        responder(request, response, mapper, 401, "USUARIO_INACTIVO");
                        return;
                    }
                    if (!id.equals(usuario.getId())
                            || !usuario.getVersionSeguridad()
                                    .equals(((Number) claims.get("versionSeguridad")).longValue())
                            || !usuario.getRol().equals(claims.get("rol", String.class))
                            || !usuario.getCorreo().equals(claims.get("correo", String.class))) {
                        responder(request, response, mapper, 401, "SESION_VENCIDA");
                        return;
                    }
                    autoridad = "ROLE_" + usuario.getRol();
                }
                SecurityContextHolder.getContext()
                        .setAuthentication(
                                new UsernamePasswordAuthenticationToken(
                                        claims,
                                        null,
                                        List.of(new SimpleGrantedAuthority(autoridad))));
            } catch (JwtException | IllegalArgumentException ex) {
                SecurityContextHolder.clearContext();
                responder(
                        request,
                        response,
                        mapper,
                        401,
                        ex instanceof ExpiredJwtException ? "SESION_VENCIDA" : "TOKEN_INVALIDO");
                return;
            } catch (FeignException ex) {
                SecurityContextHolder.clearContext();
                int status = ex.status() == 404 ? 401 : 503;
                responder(
                        request,
                        response,
                        mapper,
                        status,
                        status == 401 ? "SESION_VENCIDA" : "DEPENDENCIA_NO_DISPONIBLE");
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
