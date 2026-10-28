package com.fastfood.order.dto;

import com.fastfood.order.model.MensajeChat;
import lombok.Data;
import java.time.Instant;
import java.util.UUID;

@Data
public class MensajeChatResponse {

    private Long id;
    private Long conversacionId;
    private Long secuencia;
    private UUID clientMessageId;
    private Long remitenteId;
    private String remitenteNombre;
    private String rolRemitente;
    private String contenido;
    private Instant fechaHora;

    public static MensajeChatResponse de(MensajeChat mensaje) {
        MensajeChatResponse dto = new MensajeChatResponse();
        dto.setId(mensaje.getId());
        dto.setConversacionId(mensaje.getConversacion().getId());
        dto.setSecuencia(mensaje.getSecuencia());
        dto.setClientMessageId(mensaje.getClientMessageId());
        dto.setRemitenteId(mensaje.getRemitenteId());
        dto.setRemitenteNombre(mensaje.getRemitenteNombre());
        dto.setRolRemitente(mensaje.getRolRemitente());
        dto.setContenido(mensaje.getContenido());
        dto.setFechaHora(mensaje.getFechaHora());
        return dto;
    }
}
