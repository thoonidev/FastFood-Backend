package com.fastfood.catalog.controller;

import com.fastfood.catalog.dto.*;
import com.fastfood.catalog.service.CatalogoValidacionService;

import jakarta.validation.Valid;

import lombok.RequiredArgsConstructor;

import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/internal/catalogo")
@RequiredArgsConstructor
public class InternalCatalogoController {

    private final CatalogoValidacionService catalogo;

    @PostMapping("/validar")
    public CatalogoValidacionResponse validar(@Valid @RequestBody CatalogoValidarRequest request) {
        return catalogo.validar(request);
    }
}
