package com.fastfood.gateway.security;

import io.jsonwebtoken.*;
import io.jsonwebtoken.security.Keys;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Set;

import javax.crypto.SecretKey;

@Component
public class JwtUtil {
    private final SecretKey key;

    public JwtUtil(@Value("${fastfood.jwt.secret}") String secreto) {
        key = Keys.hmacShaKeyFor(secreto.getBytes(StandardCharsets.UTF_8));
    }

    public Claims validar(String token) {
        var firmado =
                Jwts.parser()
                        .verifyWith(key)
                        .requireIssuer("fastfood-auth")
                        .clockSkewSeconds(30)
                        .build()
                        .parseSignedClaims(token);
        if (!"HS256".equals(firmado.getHeader().getAlgorithm()))
            throw new JwtException("Algoritmo invalido");
        Claims claims = firmado.getPayload();
        long id = enteroPositivo(claims.get("usuarioId"));
        enteroPositivo(claims.get("versionSeguridad"));
        String rol = claims.get("rol", String.class);
        String correo = claims.get("correo", String.class);
        if (!Long.toString(id).equals(claims.getSubject())
                || !Set.of("CLIENTE", "EMPLEADO", "ADMINISTRADOR").contains(rol == null ? "" : rol)
                || correo == null
                || correo.isBlank()
                || claims.containsKey("scope")
                || claims.getIssuedAt() == null
                || claims.getExpiration() == null) throw new JwtException("Identidad invalida");
        Instant inicio = claims.getIssuedAt().toInstant(), fin = claims.getExpiration().toInstant();
        if (inicio.isAfter(Instant.now().plusSeconds(30))
                || !fin.isAfter(inicio)
                || fin.getEpochSecond() - inicio.getEpochSecond() > 3600)
            throw new JwtException("Fechas invalidas");
        return claims;
    }

    private long enteroPositivo(Object valor) {
        if (!(valor instanceof Integer || valor instanceof Long)
                || ((Number) valor).longValue() <= 0)
            throw new JwtException("Identificador invalido");
        return ((Number) valor).longValue();
    }
}
