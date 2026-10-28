package com.fastfood.auth.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.ToString;

import java.time.Instant;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class LoginResponse {

    @ToString.Exclude private String token;

    private String tipo;
    private Instant expiresAt;
    private UsuarioResponse usuario;
}
