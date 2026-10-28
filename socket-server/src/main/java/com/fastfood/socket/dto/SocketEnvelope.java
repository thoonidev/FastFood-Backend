package com.fastfood.socket.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Data;
import java.time.Instant;
import java.util.*;

@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
public class SocketEnvelope {
    private int version = 1;
    private String tipo;
    private UUID requestId;
    private Long idPedido;
    private UUID eventId;
    private Instant generadoEn;
    private Map<String, Object> payload = Map.of();
}
