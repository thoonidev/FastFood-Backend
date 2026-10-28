package com.fastfood.order.controller;

import com.fastfood.order.dto.ComprobanteResponse;
import com.fastfood.order.service.ComprobanteFacade;
import io.jsonwebtoken.Claims;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import java.net.URI;

@RestController
@RequestMapping("/api/pedidos/{id:[1-9][0-9]*}/comprobante")
@RequiredArgsConstructor
public class ComprobanteController {
    private final ComprobanteFacade comprobantes;

    @PostMapping
    public ResponseEntity<ComprobanteResponse> generar(@PathVariable Long id, @AuthenticationPrincipal Claims actor) {
        var resultado = comprobantes.generar(id, actor);
        return ResponseEntity.status(resultado.isCreado() ? 201 : 200)
                .location(URI.create("/api/pedidos/" + id + "/comprobante")).body(resultado.getComprobante());
    }

    @GetMapping
    public ComprobanteResponse consultar(@PathVariable Long id, @AuthenticationPrincipal Claims actor) {
        return comprobantes.consultar(id, actor);
    }
}
