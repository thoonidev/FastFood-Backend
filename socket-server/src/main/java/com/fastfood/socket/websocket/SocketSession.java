package com.fastfood.socket.websocket;

import com.fastfood.socket.dto.UsuarioEstadoResponse;
import lombok.Data;
import lombok.ToString;
import lombok.EqualsAndHashCode;
import org.java_websocket.WebSocket;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Data
public class SocketSession {
    private final WebSocket conexion;
    private final UUID connectionId = UUID.randomUUID();
    private final Instant deadline = Instant.now().plusSeconds(5);
    private final Set<Long> suscripciones = ConcurrentHashMap.newKeySet();
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    private final Queue<String> pendientes = new ArrayDeque<>();
    private final Queue<Instant> comandos = new ArrayDeque<>();

    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    private volatile String token;

    private volatile UsuarioEstadoResponse usuario;
    private volatile Instant expiresAt;
    private boolean atendiendo;
    private boolean cerrada;
}
