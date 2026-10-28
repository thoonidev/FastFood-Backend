package com.fastfood.gateway;
import com.fastfood.gateway.security.JwtUtil;
import io.jsonwebtoken.*;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.time.*;
import java.util.Date;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class JwtUtilTests {
    private static final String SECRET = "fastfood_clave_jwt_local_academica_2026_64_caracteres_minimos_demo";
    private static final Instant NOW = Instant.now().truncatedTo(java.time.temporal.ChronoUnit.SECONDS);
    private final JwtUtil jwt = new JwtUtil(SECRET);
    private JwtBuilder usuario() {
        return Jwts.builder().issuer("fastfood-auth").subject("1").claim("usuarioId", 1L)
                .claim("correo", "cliente@fastfood.local").claim("rol", "CLIENTE").claim("versionSeguridad", 1L)
                .issuedAt(Date.from(NOW)).expiration(Date.from(NOW.plusSeconds(3600)));
    }
    private String firmar(JwtBuilder builder) {
        return builder.signWith(Keys.hmacShaKeyFor(SECRET.getBytes(StandardCharsets.UTF_8)), Jwts.SIG.HS256).compact();
    }
    @Test void aceptaIdentidadConFirmaEmisorYClaimsCorrectos() {
        assertEquals("1", jwt.validar(firmar(usuario())).getSubject());
    }
    @Test void rechazaFirmaAjenaEmisorIncorrectoYTokenTecnico() {
        assertThrows(JwtException.class, () -> jwt.validar(usuario().signWith(
                Keys.hmacShaKeyFor(("otra_clave_distinta_"+SECRET).getBytes(StandardCharsets.UTF_8))).compact()));
        assertThrows(JwtException.class, () -> jwt.validar(firmar(usuario().issuer("otro"))));
        assertThrows(JwtException.class, () -> jwt.validar(firmar(usuario().claim("scope", "USUARIO_ESTADO"))));
    }
    @Test void rechazaIdentidadInconsistenteRolAjenoYVersionInvalida() {
        assertThrows(JwtException.class, () -> jwt.validar(firmar(usuario().subject("2"))));
        assertThrows(JwtException.class, () -> jwt.validar(firmar(usuario().claim("rol", "ADMIN"))));
        assertThrows(JwtException.class, () -> jwt.validar(firmar(usuario().claim("versionSeguridad", 0))));
        assertThrows(JwtException.class, () -> jwt.validar(firmar(usuario().claim("usuarioId", 1.5))));
    }
    @Test void rechazaExpiradoFechasFuturasYDuracionExcesiva() {
        assertThrows(ExpiredJwtException.class, () -> jwt.validar(firmar(usuario()
                .issuedAt(Date.from(NOW.minusSeconds(120))).expiration(Date.from(NOW.minusSeconds(60))))));
        assertThrows(JwtException.class, () -> jwt.validar(firmar(usuario().issuedAt(Date.from(NOW.plusSeconds(60))))));
        assertThrows(JwtException.class, () -> jwt.validar(firmar(usuario().expiration(Date.from(NOW.plusSeconds(3601))))));
    }
}
