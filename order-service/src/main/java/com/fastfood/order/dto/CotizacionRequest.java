package com.fastfood.order.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;

import lombok.Data;

import java.util.List;

@Data
public class CotizacionRequest {

    @NotEmpty
    @Size(max = 40)
    private List<@NotNull @Valid Linea> detalles;

    @Data
    public static class Linea {
        @NotNull @Positive private Long productoId;

        @NotNull
        @Min(1)
        @Max(99)
        private Integer cantidad;

        @Size(max = 250)
        private String observacion;
    }
}
