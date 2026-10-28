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
public class LoginRequest {

    @NotBlank
    @Email
    @Size(max = 150)
    private String correo;

    @NotBlank
    @Size(min = 8, max = 72)
    @JsonProperty(access = JsonProperty.Access.WRITE_ONLY)
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    private String password;

    public LoginRequest(String correo, String password) {
        setCorreo(correo);
        this.password = password;
    }

    public void setCorreo(String correo) {
        this.correo = correo == null ? null : correo.trim().toLowerCase(Locale.ROOT);
    }
}
