package com.fastfood.order.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import java.util.List;

@Data
@AllArgsConstructor
public class MensajeHistorialResponse {

    private List<MensajeChatResponse> content;
    private Long ultimaSecuencia;
    private Long ultimaSecuenciaConversacion;
    private boolean hayMas;
}
