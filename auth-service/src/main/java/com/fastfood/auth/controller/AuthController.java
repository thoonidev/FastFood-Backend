package com.fastfood.auth.controller;

import com.fastfood.auth.dto.*;
import com.fastfood.auth.model.Usuario.Rol;
import com.fastfood.auth.service.*;

import io.jsonwebtoken.Claims;

import jakarta.validation.Valid;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.net.URI;

@RestController
@RequestMapping("/api/auth")
public class AuthController {
    private final AuthService auth;
    private final UsuarioService usuarios;

    public AuthController(AuthService auth, UsuarioService usuarios) {
        this.auth = auth;
        this.usuarios = usuarios;
    }

    @PostMapping("/register")
    public ResponseEntity<UsuarioResponse> register(@Valid @RequestBody RegistroRequest request) {
        UsuarioResponse usuario = usuarios.registrar(request, Rol.CLIENTE);
        return ResponseEntity.created(URI.create("/api/auth/me")).body(usuario);
    }

    @PostMapping("/login")
    public LoginResponse login(@Valid @RequestBody LoginRequest request) {
        return auth.login(request);
    }

    @GetMapping("/me")
    public UsuarioResponse me(@AuthenticationPrincipal Claims identidad) {
        return usuarios.obtener(((Number) identidad.get("usuarioId")).longValue());
    }
}
