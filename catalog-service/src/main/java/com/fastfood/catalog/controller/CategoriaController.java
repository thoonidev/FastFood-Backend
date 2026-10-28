package com.fastfood.catalog.controller;

import com.fastfood.catalog.dto.*;
import com.fastfood.catalog.service.*;

import jakarta.validation.Valid;

import lombok.RequiredArgsConstructor;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.net.URI;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class CategoriaController {

    private final CategoriaService categorias;

    @GetMapping("/categorias")
    public PaginaResponse<CategoriaResponse> publicas(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return categorias.listar(false, null, null, page, size);
    }

    @GetMapping("/admin/categorias")
    public PaginaResponse<CategoriaResponse> administrar(
            @RequestParam(required = false) Boolean activo,
            @RequestParam(required = false) String q,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return categorias.listar(true, activo, q, page, size);
    }

    @PostMapping("/admin/categorias")
    public ResponseEntity<CategoriaResponse> crear(
            @Valid @RequestBody CategoriaCrearRequest request) {
        CategoriaResponse categoria = categorias.crear(request);
        return ResponseEntity.created(URI.create("/api/admin/categorias")).body(categoria);
    }

    @PutMapping("/admin/categorias/{id}")
    public CategoriaResponse actualizar(
            @PathVariable Long id, @Valid @RequestBody CategoriaActualizarRequest request) {
        return categorias.actualizar(id, request);
    }

    @PatchMapping("/admin/categorias/{id}/estado")
    public CategoriaResponse cambiarEstado(
            @PathVariable Long id, @Valid @RequestBody CategoriaEstadoRequest request) {
        return categorias.cambiarEstado(id, request);
    }
}
