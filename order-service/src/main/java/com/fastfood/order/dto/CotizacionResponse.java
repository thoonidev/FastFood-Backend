package com.fastfood.order.dto;

import com.fastfood.order.model.Cotizacion;

import lombok.Data;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;

@Data
public class CotizacionResponse {

    private UUID id;
    private Instant expiraEn;
    private List<DetallePedidoResponse> detalles;
    private BigDecimal total;

    public static CotizacionResponse de(Cotizacion cotizacion) {
        CotizacionResponse dto = new CotizacionResponse();
        dto.setId(cotizacion.getId());
        dto.setExpiraEn(cotizacion.getExpiraEn());
        dto.setDetalles(cotizacion.getDetalles().stream().map(DetallePedidoResponse::de).toList());
        dto.setTotal(cotizacion.getTotal());
        return dto;
    }
}
