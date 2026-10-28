package com.fastfood.auth.service;

import com.fastfood.auth.dto.*;
import com.fastfood.auth.model.Usuario.EstadoUsuario;
import com.fastfood.auth.exception.NegocioException;
import com.fastfood.auth.repository.UsuarioRepository;
import com.fastfood.auth.security.JwtUtil;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthService {
    private final UsuarioRepository usuarios;
    private final PasswordEncoder passwords;
    private final JwtUtil jwt;

    public AuthService(UsuarioRepository usuarios, PasswordEncoder passwords, JwtUtil jwt) {
        this.usuarios = usuarios;
        this.passwords = passwords;
        this.jwt = jwt;
    }

    @Transactional(readOnly = true)
    public LoginResponse login(LoginRequest request) {
        UsuarioService.validarPassword(request.getPassword());
        var usuario = usuarios.findByCorreo(request.getCorreo()).orElseThrow(() -> credenciales());
        if (usuario.getEstado() != EstadoUsuario.ACTIVO
                || !passwords.matches(request.getPassword(), usuario.getPasswordHash()))
            throw credenciales();
        var token = jwt.generar(usuario);
        return new LoginResponse(
                token,
                "Bearer",
                jwt.validar(token).getExpiration().toInstant(),
                UsuarioResponse.de(usuario));
    }

    private NegocioException credenciales() {
        return new NegocioException(
                401, "CREDENCIALES_INVALIDAS", "Correo o contraseña incorrectos.");
    }
}
