package com.fastfood.order.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import java.math.BigDecimal;
import java.util.List;

@Data
@AllArgsConstructor
public class PagoConfiguracionResponse {

    private String moneda;
    private Yape yape;
    private Efectivo efectivo;

    @Data
    @AllArgsConstructor
    public static class Yape {
        private String qrUrl;
        private String nombreComercio;
        private String instrucciones;
    }

    @Data
    @AllArgsConstructor
    public static class Efectivo {
        private List<BigDecimal> montosSugeridos;
    }
}
