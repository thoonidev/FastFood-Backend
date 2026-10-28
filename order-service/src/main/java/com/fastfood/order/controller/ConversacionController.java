package com.fastfood.order.controller;

import com.fastfood.order.dto.*;
import com.fastfood.order.model.Conversacion.EstadoConversacion;
import com.fastfood.order.service.*;
import io.jsonwebtoken.Claims;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class ConversacionController {

    private final ConversacionService conversaciones;
    private final MensajeChatService mensajes;

    @GetMapping("/pedidos/{id:[1-9][0-9]*}/conversacion")
    public ConversacionResponse contexto(@PathVariable Long id, @AuthenticationPrincipal Claims actor) {
        return conversaciones.consultarAcceso(id, actor);
    }

    @GetMapping("/pedidos/{id:[1-9][0-9]*}/mensajes")
    public MensajeHistorialResponse historial(@PathVariable Long id, @AuthenticationPrincipal Claims actor,
            @RequestParam(defaultValue = "0") long afterSecuencia, @RequestParam(defaultValue = "50") int limit) {
        return mensajes.historial(id, afterSecuencia, limit, actor);
    }

    @GetMapping("/conversaciones")
    public PaginaResponse<ConversacionResponse> bandeja(@AuthenticationPrincipal Claims actor,
            @RequestParam(required = false) Boolean pendienteRespuesta,
            @RequestParam(required = false) EstadoConversacion estado,
            @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size) {
        return conversaciones.listar(pendienteRespuesta, estado, page, size, actor);
    }
}
