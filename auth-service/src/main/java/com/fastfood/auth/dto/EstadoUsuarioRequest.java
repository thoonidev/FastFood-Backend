package com.fastfood.auth.dto;

import com.fastfood.auth.model.Usuario.EstadoUsuario;

import jakarta.validation.constraints.*;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class EstadoUsuarioRequest {

    @NotNull private EstadoUsuario estado;

    @NotNull @Positive private Long versionSeguridadEsperada;
}
