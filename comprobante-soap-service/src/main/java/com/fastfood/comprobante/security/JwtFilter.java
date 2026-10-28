package com.fastfood.comprobante.security;

import com.fastfood.comprobante.exception.GlobalExceptionHandler;
import io.jsonwebtoken.*;
import io.jsonwebtoken.security.Keys;
import jakarta.servlet.*;
import jakarta.servlet.http.*;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;
import tools.jackson.databind.json.JsonMapper;
import javax.crypto.SecretKey;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.*;

public class JwtFilter extends OncePerRequestFilter {
    private final SecretKey key;
    private final JsonMapper mapper;

    public JwtFilter(String secret, JsonMapper mapper) {
        key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.mapper = mapper;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String header = request.getHeader("Authorization");
        if (header != null) {
            try {
                if (!header.startsWith("Bearer ")) {
                    throw new JwtException("Bearer invalido");
                }
                var signed = Jwts.parser().verifyWith(key).requireIssuer("fastfood-internal")
                        .clockSkewSeconds(30).build().parseSignedClaims(header.substring(7));
                Claims claims = signed.getPayload();
                String scope = claims.get("scope", String.class);
                if (!"HS256".equals(signed.getHeader().getAlgorithm()) || !"order-service".equals(claims.getSubject())
                        || !Set.of("comprobante-soap-service").equals(claims.getAudience())
                        || claims.containsKey("rol") || claims.containsKey("usuarioId")
                        || !Set.of("COMPROBANTE_OPERAR", "OBSERVABILIDAD").contains(scope == null ? "" : scope)
                        || claims.getIssuedAt() == null || claims.getExpiration() == null || claims.getId() == null
                        || !UUID.fromString(claims.getId()).toString().equalsIgnoreCase(claims.getId())) {
                    throw new JwtException("Credencial tecnica invalida");
                }
                Instant inicio = claims.getIssuedAt().toInstant(), fin = claims.getExpiration().toInstant();
                if (inicio.isAfter(Instant.now().plusSeconds(30)) || !fin.isAfter(inicio)
                        || fin.getEpochSecond() - inicio.getEpochSecond() > 60) {
                    throw new JwtException("Fechas invalidas");
                }
                SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                        claims, null, List.of(new SimpleGrantedAuthority("SCOPE_" + scope))));
            } catch (JwtException | IllegalArgumentException ex) {
                SecurityContextHolder.clearContext();
                response.setStatus(401);
                response.setContentType("application/json");
                response.setCharacterEncoding("UTF-8");
                mapper.writeValue(response.getWriter(), GlobalExceptionHandler.errorBody(401, "TOKEN_INVALIDO", request.getRequestURI()));
                return;
            }
        }
        chain.doFilter(request, response);
    }
}
