package com.fastfood.order.controller;

import com.fastfood.order.dto.*;
import com.fastfood.order.service.PagoService;
import io.jsonwebtoken.Claims;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class PagoController {

    private final PagoService pagos;

    @GetMapping("/pagos/configuracion")
    public PagoConfiguracionResponse configuracion() {
        return pagos.configuracion();
    }

    @GetMapping("/pedidos/{id:[1-9][0-9]*}/pago")
    public PagoResponse obtener(@PathVariable Long id, @AuthenticationPrincipal Claims actor) {
        return pagos.obtener(id, actor);
    }

    @GetMapping("/pedidos/{id:[1-9][0-9]*}/pago/intentos")
    public PaginaResponse<PagoIntentoResponse> intentos(@PathVariable Long id,
            @AuthenticationPrincipal Claims actor, @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return pagos.listarIntentos(id, actor, page, size);
    }

    @PostMapping("/pedidos/{id:[1-9][0-9]*}/pago/yape")
    public ResponseEntity<PagoResponse> enviarYape(@PathVariable Long id,
            @Valid @RequestBody YapeEnviarRequest request, @RequestHeader("Idempotency-Key") String clave,
            @AuthenticationPrincipal Claims actor) {
        return responder(pagos.enviarYape(id, request, actor, clave));
    }

    @PostMapping("/pedidos/{id:[1-9][0-9]*}/pago/yape/validacion")
    public ResponseEntity<PagoResponse> resolverYape(@PathVariable Long id,
            @Valid @RequestBody YapeResolverRequest request, @RequestHeader("Idempotency-Key") String clave,
            @AuthenticationPrincipal Claims actor) {
        return responder(pagos.resolverYape(id, request, actor, clave));
    }

    @PostMapping("/pedidos/{id:[1-9][0-9]*}/pago/tarjeta")
    public ResponseEntity<PagoResponse> tarjeta(@PathVariable Long id,
            @Valid @RequestBody TarjetaProcesarRequest request, @RequestHeader("Idempotency-Key") String clave,
            @AuthenticationPrincipal Claims actor) {
        return responder(pagos.procesarTarjeta(id, request, actor, clave));
    }

    @PostMapping("/pedidos/{id:[1-9][0-9]*}/pago/efectivo/confirmacion")
    public ResponseEntity<PagoResponse> efectivo(@PathVariable Long id,
            @Valid @RequestBody EfectivoConfirmarRequest request, @RequestHeader("Idempotency-Key") String clave,
            @AuthenticationPrincipal Claims actor) {
        return responder(pagos.confirmarEfectivo(id, request, actor, clave));
    }

    private ResponseEntity<PagoResponse> responder(ResultadoPago resultado) {
        return ResponseEntity.status(resultado.getStatus())
                .header("Idempotency-Replayed", Boolean.toString(resultado.isReplayed()))
                .body(resultado.getPago());
    }
}
