package com.fastfood.auth.controller;

import com.fastfood.auth.dto.*;
import com.fastfood.auth.model.Usuario.Rol;
import com.fastfood.auth.model.Usuario.EstadoUsuario;
import com.fastfood.auth.service.UsuarioService;

import io.jsonwebtoken.Claims;

import jakarta.validation.Valid;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/usuarios")
public class UsuarioController {
    private final UsuarioService usuarios;

    public UsuarioController(UsuarioService usuarios) {
        this.usuarios = usuarios;
    }

    @PutMapping("/me")
    public UsuarioResponse perfil(
            @AuthenticationPrincipal Claims identidad, @Valid @RequestBody PerfilRequest request) {
        return usuarios.actualizarPerfil(
                ((Number) identidad.get("usuarioId")).longValue(), request, false);
    }

    @GetMapping
    public PaginaResponse<UsuarioResponse> listar(
            @RequestParam(required = false) Rol rol,
            @RequestParam(required = false) EstadoUsuario estado,
            @RequestParam(required = false) String q,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return usuarios.listar(rol, estado, q, page, size);
    }
}
