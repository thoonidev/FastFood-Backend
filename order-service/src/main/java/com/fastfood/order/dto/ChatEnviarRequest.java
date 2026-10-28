package com.fastfood.order.dto;

import jakarta.validation.constraints.*;
import lombok.Data;
import java.util.UUID;

@Data
public class ChatEnviarRequest {

    @NotNull
    private UUID clientMessageId;

    @NotBlank
    @Size(max = 1000)
    private String contenido;
}
