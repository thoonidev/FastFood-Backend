package com.fastfood.order.dto;

import lombok.Data;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

@Data
public class SocketEventRequest {
    private UUID eventId;
    private String tipo;
    private Instant generadoEn;
    private Long idPedido;
    private Long clienteId;
    private Map<String, Object> payload;
}
