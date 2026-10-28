package com.fastfood.auth.dto;

import com.fastfood.auth.model.Usuario.EstadoUsuario;
import com.fastfood.auth.model.Usuario.Rol;
import com.fastfood.auth.model.Usuario;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class UsuarioResponse {

    private Long id;
    private String nombres;
    private String apellidos;
    private String correo;
    private String telefono;
    private Rol rol;
    private EstadoUsuario estado;
    private Long versionSeguridad;
    private Instant fechaCreacion;
    private Instant fechaActualizacion;

    public static UsuarioResponse de(Usuario usuario) {
        return new UsuarioResponse(
                usuario.getId(),
                usuario.getNombres(),
                usuario.getApellidos(),
                usuario.getCorreo(),
                usuario.getTelefono(),
                usuario.getRol(),
                usuario.getEstado(),
                usuario.getVersionSeguridad(),
                usuario.getFechaCreacion(),
                usuario.getFechaActualizacion());
    }
}
