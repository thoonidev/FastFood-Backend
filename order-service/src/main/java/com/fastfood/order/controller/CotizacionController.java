package com.fastfood.order.controller;

import com.fastfood.order.dto.*;
import com.fastfood.order.service.CotizacionService;

import io.jsonwebtoken.Claims;

import jakarta.validation.Valid;

import lombok.RequiredArgsConstructor;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/cotizaciones")
@RequiredArgsConstructor
public class CotizacionController {

    private final CotizacionService cotizaciones;

    @PostMapping
    public ResponseEntity<CotizacionResponse> crear(
            @Valid @RequestBody CotizacionRequest request, @AuthenticationPrincipal Claims actor) {
        return ResponseEntity.status(201)
                .body(cotizaciones.crear(((Number) actor.get("usuarioId")).longValue(), request));
    }
}
