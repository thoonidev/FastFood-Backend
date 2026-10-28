package com.fastfood.catalog.dto;

import jakarta.validation.constraints.*;

import lombok.Data;

@Data
public class CategoriaCrearRequest {

    @NotBlank
    @Size(max = 80)
    private String nombre;

    @Size(max = 255)
    private String descripcion;
}
