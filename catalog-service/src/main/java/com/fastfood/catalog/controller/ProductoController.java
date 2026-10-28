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
public class ProductoController {

    private final ProductoService productos;

    @GetMapping("/productos")
    public PaginaResponse<ProductoResponse> publicos(
            @RequestParam(required = false) String q,
            @RequestParam(required = false) Long categoriaId,
            @RequestParam(required = false) Boolean destacado,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return productos.listar(false, null, null, categoriaId, destacado, q, page, size);
    }

    @GetMapping("/productos/{id}")
    public ProductoResponse detallePublico(@PathVariable Long id) {
        return productos.obtener(id, false);
    }

    @GetMapping("/admin/productos")
    public PaginaResponse<ProductoResponse> administrar(
            @RequestParam(required = false) Boolean activo,
            @RequestParam(required = false) Boolean disponible,
            @RequestParam(required = false) Long categoriaId,
            @RequestParam(required = false) String q,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return productos.listar(true, activo, disponible, categoriaId, null, q, page, size);
    }

    @GetMapping("/admin/productos/{id}")
    public ProductoResponse detalleAdministrativo(@PathVariable Long id) {
        return productos.obtener(id, true);
    }

    @PostMapping("/admin/productos")
    public ResponseEntity<ProductoResponse> crear(
            @Valid @RequestBody ProductoCrearRequest request) {
        ProductoResponse producto = productos.crear(request);
        return ResponseEntity.created(URI.create("/api/admin/productos/" + producto.getId()))
                .body(producto);
    }

    @PutMapping("/admin/productos/{id}")
    public ProductoResponse actualizar(
            @PathVariable Long id, @Valid @RequestBody ProductoActualizarRequest request) {
        return productos.actualizar(id, request);
    }

    @PatchMapping("/admin/productos/{id}/estado")
    public ProductoResponse cambiarEstado(
            @PathVariable Long id, @Valid @RequestBody ProductoEstadoRequest request) {
        return productos.cambiarEstado(id, request);
    }
}
