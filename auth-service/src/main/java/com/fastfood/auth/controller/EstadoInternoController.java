package com.fastfood.auth.controller;

import com.fastfood.auth.dto.UsuarioResponse;
import com.fastfood.auth.service.UsuarioService;

import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/internal/auth/usuarios")
public class EstadoInternoController {
    private final UsuarioService usuarios;

    public EstadoInternoController(UsuarioService usuarios) {
        this.usuarios = usuarios;
    }

    @GetMapping("/{id}/estado")
    public UsuarioResponse estado(@PathVariable Long id) {
        return usuarios.obtener(id);
    }
}
