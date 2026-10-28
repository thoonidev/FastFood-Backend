package com.fastfood.order.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class ResultadoPago {

    private PagoResponse pago;
    private int status;
    private boolean replayed;
}
