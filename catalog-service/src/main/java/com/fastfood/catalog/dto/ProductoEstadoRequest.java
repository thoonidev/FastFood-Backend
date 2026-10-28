package com.fastfood.catalog.dto;

import jakarta.validation.constraints.*;

import lombok.Data;

@Data
public class ProductoEstadoRequest {

    @NotNull private Boolean activo;

    @NotNull private Boolean disponible;

    @NotNull @PositiveOrZero private Long versionEsperada;
}
