package com.fastfood.catalog.dto;

import jakarta.validation.constraints.*;

import lombok.Data;

import java.math.BigDecimal;

@Data
public class ProductoCrearRequest {

    @NotNull @Positive private Long categoriaId;

    @NotBlank
    @Size(max = 100)
    private String nombre;

    @NotBlank
    @Size(max = 500)
    private String descripcion;

    @NotNull
    @DecimalMin("0.01")
    @DecimalMax("9999999999.99")
    @Digits(integer = 10, fraction = 2)
    private BigDecimal precio;

    @NotBlank
    @Size(max = 2048)
    @Pattern(regexp = "(?i)^https?://\\S+$")
    private String imagenUrl;

    private Boolean destacado;
}
