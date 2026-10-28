package com.fastfood.order.dto;

import com.fastfood.order.model.PagoIntento;
import com.fastfood.order.model.PagoIntento.EstadoIntento;
import lombok.Data;
import java.time.Instant;

@Data
public class PagoIntentoResponse {

    private Long id;
    private Integer numeroIntento;
    private EstadoIntento estado;
    private String referenciaOperacion;
    private String motivoRechazo;
    private Instant creadoEn;
    private Instant resueltoEn;
    private Long resueltoPorId;

    public static PagoIntentoResponse de(PagoIntento intento) {
        PagoIntentoResponse dto = new PagoIntentoResponse();
        dto.setId(intento.getId());
        dto.setNumeroIntento(intento.getNumeroIntento());
        dto.setEstado(intento.getEstado());
        dto.setReferenciaOperacion(intento.getReferenciaOperacion());
        dto.setMotivoRechazo(intento.getMotivoRechazo());
        dto.setCreadoEn(intento.getCreadoEn());
        dto.setResueltoEn(intento.getResueltoEn());
        dto.setResueltoPorId(intento.getResueltoPorId());
        return dto;
    }
}
