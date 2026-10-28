package com.fastfood.order.dto;

import com.fastfood.order.model.Pago;
import com.fastfood.order.model.Pago.*;

import lombok.Data;

import java.math.BigDecimal;
import java.time.Instant;

@Data
public class PagoResponse {

    private Long id;
    private MetodoPago metodoPago;
    private BigDecimal monto;
    private EstadoPago estadoPago;
    private Long version;
    private BigDecimal montoAnunciado;
    private BigDecimal montoRecibido;
    private BigDecimal vueltoEstimado;
    private BigDecimal vueltoFinal;
    private Instant fechaPago;
    private String referenciaOperacion;
    private Long intentoActualId;

    public static PagoResponse de(Pago pago) {
        PagoResponse dto = new PagoResponse();
        dto.setId(pago.getId());
        dto.setMetodoPago(pago.getMetodoPago());
        dto.setMonto(pago.getMonto());
        dto.setEstadoPago(pago.getEstadoPago());
        dto.setVersion(pago.getVersion());
        dto.setMontoAnunciado(pago.getMontoAnunciado());
        dto.setMontoRecibido(pago.getMontoRecibido());
        dto.setVueltoEstimado(pago.getVueltoEstimado());
        dto.setVueltoFinal(pago.getVueltoFinal());
        dto.setFechaPago(pago.getFechaPago());
        dto.setReferenciaOperacion(pago.getReferenciaOperacion());
        dto.setIntentoActualId(pago.getIntentoActualId());
        return dto;
    }
}
