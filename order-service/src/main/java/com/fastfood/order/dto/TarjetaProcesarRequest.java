package com.fastfood.order.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.*;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;

@Data
public class TarjetaProcesarRequest {

    @NotBlank
    @Size(max = 40)
    @Pattern(regexp = "[0-9 -]+", message = "Ingresa un numero de tarjeta valido")
    @JsonProperty(access = JsonProperty.Access.WRITE_ONLY)
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    private String numeroTarjeta;

    @NotBlank
    @Size(max = 100)
    @JsonProperty(access = JsonProperty.Access.WRITE_ONLY)
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    private String titular;

    @NotNull
    @Min(1)
    @Max(12)
    @JsonProperty(access = JsonProperty.Access.WRITE_ONLY)
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    private Integer mesVencimiento;

    @NotNull
    @JsonProperty(access = JsonProperty.Access.WRITE_ONLY)
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    private Integer anioVencimiento;

    @NotBlank
    @Pattern(regexp = "[0-9]{3}", message = "El codigo de seguridad debe tener tres digitos")
    @JsonProperty(access = JsonProperty.Access.WRITE_ONLY)
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    private String codigoSeguridad;

    @NotNull
    @PositiveOrZero
    private Long versionPagoEsperada;
}
