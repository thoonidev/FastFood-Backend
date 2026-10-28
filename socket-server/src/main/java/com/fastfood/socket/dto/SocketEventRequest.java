package com.fastfood.socket.dto;

import jakarta.validation.constraints.*;
import lombok.Data;
import java.time.Instant;
import java.util.*;

@Data
public class SocketEventRequest {
    @NotNull
    private UUID eventId;

    @NotBlank
    @Pattern(regexp = "PEDIDO_NUEVO|PEDIDO_ACTUALIZADO|MENSAJE_NUEVO|CHAT_CERRADO")
    private String tipo;

    @NotNull
    private Instant generadoEn;

    @NotNull
    @Positive
    private Long idPedido;

    @NotNull
    @Positive
    private Long clienteId;

    @NotEmpty
    private Map<String, Object> payload;
}
