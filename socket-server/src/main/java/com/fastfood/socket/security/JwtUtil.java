package com.fastfood.socket.security;

import io.jsonwebtoken.*;
import io.jsonwebtoken.security.Keys;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.Set;
import java.util.UUID;

import javax.crypto.SecretKey;

@Component
public class JwtUtil {
    private final SecretKey key;
    private final SecretKey internalKey;

    public JwtUtil(
            @Value("${fastfood.jwt.secret}") String secreto,
            @Value("${fastfood.internal.secret}") String interno) {
        key = Keys.hmacShaKeyFor(secreto.getBytes(StandardCharsets.UTF_8));
        internalKey = Keys.hmacShaKeyFor(interno.getBytes(StandardCharsets.UTF_8));
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

    public String generarTecnicoAuth() {
        Instant ahora = Instant.now();
        return Jwts.builder()
                .issuer("fastfood-internal")
                .subject("socket-server")
                .audience()
                .add("auth-service")
                .and()
                .claim("scope", "USUARIO_ESTADO")
                .id(UUID.randomUUID().toString())
                .issuedAt(Date.from(ahora))
                .expiration(Date.from(ahora.plusSeconds(60)))
                .signWith(internalKey, Jwts.SIG.HS256)
                .compact();
    }

    public Claims validarTecnico(String token) {
        var firmado =
                Jwts.parser()
                        .verifyWith(internalKey)
                        .requireIssuer("fastfood-internal")
                        .clockSkewSeconds(30)
                        .build()
                        .parseSignedClaims(token);
        Claims claims = firmado.getPayload();
        if (!"HS256".equals(firmado.getHeader().getAlgorithm())
                || !Set.of("order-service")
                        .contains(claims.getSubject() == null ? "" : claims.getSubject())
                || claims.getAudience() == null
                || !claims.getAudience().equals(Set.of("socket-server"))
                || !"EVENTO_PUBLICAR".equals(claims.get("scope", String.class))
                || claims.containsKey("rol")
                || claims.getIssuedAt() == null
                || claims.getExpiration() == null
                || claims.getId() == null) throw new JwtException("Credencial tecnica invalida");
        try {
            if (!UUID.fromString(claims.getId()).toString().equalsIgnoreCase(claims.getId()))
                throw new JwtException("Identificador tecnico invalido");
        } catch (IllegalArgumentException ex) {
            throw new JwtException("Identificador tecnico invalido");
        }
        Instant inicio = claims.getIssuedAt().toInstant(), fin = claims.getExpiration().toInstant();
        if (inicio.isAfter(Instant.now().plusSeconds(30))
                || !fin.isAfter(inicio)
                || fin.getEpochSecond() - inicio.getEpochSecond() > 60)
            throw new JwtException("Fechas tecnicas invalidas");
        return claims;
    }
}
