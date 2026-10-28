package com.fastfood.auth.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import jakarta.validation.constraints.*;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.ToString;

import java.util.Locale;

@Data
@NoArgsConstructor
public class RegistroRequest {

    @NotBlank
    @Size(max = 100)
    private String nombres;

    @NotBlank
    @Size(max = 100)
    private String apellidos;

    @NotBlank
    @Email
    @Size(max = 150)
    private String correo;

    @NotBlank
    @Pattern(regexp = "[0-9+ \\-]{6,20}")
    private String telefono;

    @NotBlank
    @Size(min = 8, max = 72)
    @JsonProperty(access = JsonProperty.Access.WRITE_ONLY)
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    private String password;

    public RegistroRequest(
            String nombres, String apellidos, String correo, String telefono, String password) {
        this.nombres = nombres;
        this.apellidos = apellidos;
        setCorreo(correo);
        this.telefono = telefono;
        this.password = password;
    }

    public void setCorreo(String correo) {
        this.correo = correo == null ? null : correo.trim().toLowerCase(Locale.ROOT);
    }
}
