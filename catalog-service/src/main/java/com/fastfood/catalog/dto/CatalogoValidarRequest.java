package com.fastfood.catalog.dto;

import jakarta.validation.constraints.*;

import lombok.Data;

import java.util.List;

@Data
public class CatalogoValidarRequest {

    @NotEmpty
    @Size(max = 40)
    private List<@NotNull @Positive Long> productoIds;
}
