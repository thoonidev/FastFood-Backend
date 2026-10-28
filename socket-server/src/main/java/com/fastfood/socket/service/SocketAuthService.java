package com.fastfood.socket.service;

import com.fastfood.socket.dto.UsuarioEstadoResponse;
import com.fastfood.socket.exception.NegocioException;
import com.fastfood.socket.feign.AuthEstadoFeignClient;
import com.fastfood.socket.security.JwtUtil;
import feign.FeignException;
import io.jsonwebtoken.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import java.time.Instant;
import java.util.Objects;

@Service
@RequiredArgsConstructor
public class SocketAuthService {
    private final JwtUtil jwt;
    private final AuthEstadoFeignClient auth;

    public Claims validarToken(String token) {
        try {
            Claims claims = jwt.validar(token);
            if (!claims.getExpiration().toInstant().isAfter(Instant.now())) {
                throw new JwtException("Sesion vencida");
            }
            return claims;
        } catch (JwtException | IllegalArgumentException ex) {
            throw new NegocioException(401, "SESION_INVALIDA", "La sesion ya no es valida.");
        }
    }

    public UsuarioEstadoResponse validar(String token) {
        Claims claims = validarToken(token);
        long id = ((Number) claims.get("usuarioId")).longValue();
        UsuarioEstadoResponse usuario;
        try {
            usuario = auth.obtener(id, "Bearer " + jwt.generarTecnicoAuth());
        } catch (FeignException ex) {
            if (ex.status() == 404) {
                throw new NegocioException(401, "SESION_INVALIDA", "La sesion ya no es valida.");
            }
            throw new NegocioException(503, "DEPENDENCIA_NO_DISPONIBLE", "Auth no esta disponible.");
        }
        if (usuario == null || usuario.getId() == null || usuario.getNombres() == null
                || usuario.getApellidos() == null || usuario.getRol() == null || usuario.getCorreo() == null
                || usuario.getEstado() == null || usuario.getVersionSeguridad() == null) {
            throw new NegocioException(503, "DEPENDENCIA_NO_DISPONIBLE", "La respuesta de Auth no es valida.");
        }
        if (!"ACTIVO".equals(usuario.getEstado()) || !usuario.getId().equals(id)
                || !Objects.equals(usuario.getRol(), claims.get("rol"))
                || !Objects.equals(usuario.getCorreo(), claims.get("correo"))
                || usuario.getVersionSeguridad() != ((Number) claims.get("versionSeguridad")).longValue()) {
            throw new NegocioException(401, "SESION_INVALIDA", "La sesion ya no es valida.");
        }
        validarToken(token);
        return usuario;
    }
}
