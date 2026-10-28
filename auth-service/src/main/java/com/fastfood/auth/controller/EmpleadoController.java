package com.fastfood.auth.controller;

import com.fastfood.auth.dto.*;
import com.fastfood.auth.model.Usuario.Rol;
import com.fastfood.auth.model.Usuario.EstadoUsuario;
import com.fastfood.auth.service.UsuarioService;

import jakarta.validation.Valid;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.net.URI;

@RestController
@RequestMapping("/api/empleados")
public class EmpleadoController {
    private final UsuarioService usuarios;

    public EmpleadoController(UsuarioService usuarios) {
        this.usuarios = usuarios;
    }

    @GetMapping
    public PaginaResponse<UsuarioResponse> listar(
            @RequestParam(required = false) EstadoUsuario estado,
            @RequestParam(required = false) String q,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return usuarios.listar(Rol.EMPLEADO, estado, q, page, size);
    }

    @PostMapping
    public ResponseEntity<UsuarioResponse> crear(@Valid @RequestBody RegistroRequest request) {
        UsuarioResponse usuario = usuarios.registrar(request, Rol.EMPLEADO);
        return ResponseEntity.created(URI.create("/api/empleados/" + usuario.getId()))
                .body(usuario);
    }

    @PutMapping("/{id}")
    public UsuarioResponse actualizar(
            @PathVariable Long id, @Valid @RequestBody PerfilRequest request) {
        return usuarios.actualizarPerfil(id, request, true);
    }

    @PatchMapping("/{id}/estado")
    public UsuarioResponse estado(
            @PathVariable Long id, @Valid @RequestBody EstadoUsuarioRequest request) {
        return usuarios.cambiarEstado(id, request);
    }
}
