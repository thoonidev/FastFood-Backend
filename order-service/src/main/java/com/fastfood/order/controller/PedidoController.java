package com.fastfood.order.controller;

import com.fastfood.order.dto.*;
import com.fastfood.order.model.Pago.*;
import com.fastfood.order.model.Pedido.EstadoPedido;
import com.fastfood.order.service.PedidoService;

import io.jsonwebtoken.Claims;

import jakarta.validation.Valid;

import lombok.RequiredArgsConstructor;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;

@RestController
@RequestMapping("/api/pedidos")
@RequiredArgsConstructor
public class PedidoController {

    private final PedidoService pedidos;

    @PostMapping
    public ResponseEntity<PedidoDetalleResponse> crear(
            @Valid @RequestBody PedidoCrearRequest request,
            @RequestHeader("Idempotency-Key") String clave,
            @AuthenticationPrincipal Claims actor) {
        return responder(pedidos.crear(request, actor, clave));
    }

    @GetMapping("/mis-pedidos")
    public PaginaResponse<PedidoResumenResponse> misPedidos(
            @AuthenticationPrincipal Claims actor,
            @RequestParam(required = false) EstadoPedido estadoPedido,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
                    LocalDate desde,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
                    LocalDate hasta,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return pedidos.listar(
                true, actor, estadoPedido, null, null, null, desde, hasta, page, size);
    }

    @GetMapping
    public PaginaResponse<PedidoResumenResponse> listar(
            @AuthenticationPrincipal Claims actor,
            @RequestParam(required = false) EstadoPedido estadoPedido,
            @RequestParam(required = false) EstadoPago estadoPago,
            @RequestParam(required = false) MetodoPago metodoPago,
            @RequestParam(required = false) Long clienteId,
            @RequestParam(required = false) String cliente,
            @RequestParam(required = false) String numeroPedido,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
                    LocalDate desde,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
                    LocalDate hasta,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return pedidos.listar(
                false,
                actor,
                estadoPedido,
                estadoPago,
                metodoPago,
                clienteId,
                cliente,
                numeroPedido,
                desde,
                hasta,
                page,
                size);
    }

    @GetMapping("/{id:[1-9][0-9]*}")
    public PedidoDetalleResponse obtener(
            @PathVariable Long id, @AuthenticationPrincipal Claims actor) {
        return pedidos.obtener(id, actor);
    }

    @GetMapping("/{id:[1-9][0-9]*}/cambios-estado")
    public PaginaResponse<PedidoCambioEstadoResponse> historial(
            @PathVariable Long id,
            @AuthenticationPrincipal Claims actor,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return pedidos.historial(id, actor, page, size);
    }

    @PatchMapping("/{id:[1-9][0-9]*}/estado")
    public ResponseEntity<PedidoDetalleResponse> transicionar(
            @PathVariable Long id,
            @Valid @RequestBody PedidoTransicionRequest request,
            @RequestHeader("Idempotency-Key") String clave,
            @AuthenticationPrincipal Claims actor) {
        return responder(pedidos.transicionar(id, request, actor, clave));
    }

    @PostMapping("/{id:[1-9][0-9]*}/cancelacion")
    public ResponseEntity<PedidoDetalleResponse> cancelar(
            @PathVariable Long id,
            @Valid @RequestBody PedidoCancelarRequest request,
            @RequestHeader("Idempotency-Key") String clave,
            @AuthenticationPrincipal Claims actor) {
        return responder(pedidos.cancelar(id, request, actor, clave));
    }

    private ResponseEntity<PedidoDetalleResponse> responder(ResultadoPedido resultado) {
        return ResponseEntity.status(resultado.getStatus())
                .header("Location", "/api/pedidos/" + resultado.getPedido().getId())
                .header("Idempotency-Replayed", Boolean.toString(resultado.isReplayed()))
                .body(resultado.getPedido());
    }
}
