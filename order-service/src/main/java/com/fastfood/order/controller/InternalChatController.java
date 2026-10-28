package com.fastfood.order.controller;

import com.fastfood.order.dto.*;
import com.fastfood.order.service.*;
import io.jsonwebtoken.Claims;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/internal/chat/pedidos")
@RequiredArgsConstructor
public class InternalChatController {

    private final ConversacionService conversaciones;
    private final MensajeChatService mensajes;

    @PostMapping("/{id:[1-9][0-9]*}/acceso")
    public ConversacionResponse acceso(@PathVariable Long id, @AuthenticationPrincipal Claims actor) {
        return conversaciones.consultarAcceso(id, actor);
    }

    @PostMapping("/{id:[1-9][0-9]*}/mensajes")
    public MensajeChatResponse enviar(@PathVariable Long id, @Valid @RequestBody ChatEnviarRequest request,
            @AuthenticationPrincipal Claims actor) {
        return mensajes.enviar(id, request, actor);
    }
}
