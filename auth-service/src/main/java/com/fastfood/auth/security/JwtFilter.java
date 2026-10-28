package com.fastfood.auth.security;

import com.fastfood.auth.model.Usuario.EstadoUsuario;
import com.fastfood.auth.exception.GlobalExceptionHandler;
import com.fastfood.auth.repository.UsuarioRepository;

import io.jsonwebtoken.*;

import jakarta.servlet.*;
import jakarta.servlet.http.*;

import org.springframework.dao.DataAccessException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.util.List;

public class JwtFilter extends OncePerRequestFilter {
    private final JwtUtil jwt;
    private final UsuarioRepository usuarios;
    private final JsonMapper mapper;

    public JwtFilter(JwtUtil jwt, UsuarioRepository usuarios, JsonMapper mapper) {
        this.jwt = jwt;
        this.usuarios = usuarios;
        this.mapper = mapper;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return "POST".equals(request.getMethod())
                && (request.getRequestURI().equals("/api/auth/login")
                        || request.getRequestURI().equals("/api/auth/register"));
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
                Claims claims;
                String authority;
                if (request.getRequestURI().startsWith("/internal/")) {
                    claims = jwt.validarTecnico(header.substring(7));
                    if (!"GET".equals(request.getMethod())
                            || !request.getRequestURI()
                                    .matches("/internal/auth/usuarios/[1-9][0-9]*/estado")) {
                        responder(request, response, 403, "ACCESO_DENEGADO");
                        return;
                    }
                    authority = "SCOPE_USUARIO_ESTADO";
                } else {
                    claims = jwt.validar(header.substring(7));
                    var usuario =
                            usuarios.findById(((Number) claims.get("usuarioId")).longValue())
                                    .orElse(null);
                    if (usuario == null || usuario.getEstado() != EstadoUsuario.ACTIVO) {
                        responder(request, response, 401, "USUARIO_INACTIVO");
                        return;
                    }
                    if (!usuario.getVersionSeguridad()
                                    .equals(((Number) claims.get("versionSeguridad")).longValue())
                            || !usuario.getRol().name().equals(claims.get("rol", String.class))
                            || !usuario.getCorreo().equals(claims.get("correo", String.class))) {
                        responder(request, response, 401, "SESION_VENCIDA");
                        return;
                    }
                    authority = "ROLE_" + usuario.getRol().name();
                }
                SecurityContextHolder.getContext()
                        .setAuthentication(
                                new UsernamePasswordAuthenticationToken(
                                        claims,
                                        null,
                                        List.of(new SimpleGrantedAuthority(authority))));
            } catch (JwtException | IllegalArgumentException ex) {
                SecurityContextHolder.clearContext();
                responder(
                        request,
                        response,
                        401,
                        ex instanceof ExpiredJwtException ? "SESION_VENCIDA" : "TOKEN_INVALIDO");
                return;
            } catch (DataAccessException ex) {
                SecurityContextHolder.clearContext();
                responder(request, response, 503, "DEPENDENCIA_NO_DISPONIBLE");
                return;
            }
        }
        chain.doFilter(request, response);
    }

    private void responder(
            HttpServletRequest request, HttpServletResponse response, int status, String code)
            throws IOException {
        response.setStatus(status);
        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");
        mapper.writeValue(
                response.getWriter(),
                GlobalExceptionHandler.errorBody(status, code, request.getRequestURI()));
    }
}
