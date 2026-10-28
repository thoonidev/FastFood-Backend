package com.fastfood.catalog.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import lombok.Data;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class UsuarioEstadoResponse {

    private Long id;
    private String correo;
    private String rol;
    private String estado;
    private Long versionSeguridad;
}
