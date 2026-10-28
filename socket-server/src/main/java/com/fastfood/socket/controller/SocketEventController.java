package com.fastfood.socket.controller;

import com.fastfood.socket.dto.SocketEventRequest;
import com.fastfood.socket.websocket.FastFoodWebSocketServer;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.Map;

@RestController
@RequestMapping("/internal/socket/eventos")
@RequiredArgsConstructor
public class SocketEventController {
    private final FastFoodWebSocketServer socket;

    @PostMapping
    public ResponseEntity<Map<String, Object>> recibir(@Valid @RequestBody SocketEventRequest evento) {
        socket.aceptarEvento(evento);
        return ResponseEntity.accepted().body(Map.of("aceptado", true, "eventId", evento.getEventId()));
    }
}
