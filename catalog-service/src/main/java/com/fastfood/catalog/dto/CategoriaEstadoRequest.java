package com.fastfood.catalog.dto;

import jakarta.validation.constraints.*;

import lombok.Data;

@Data
public class CategoriaEstadoRequest {

    @NotNull private Boolean activo;

    @NotNull @PositiveOrZero private Long versionEsperada;
}
