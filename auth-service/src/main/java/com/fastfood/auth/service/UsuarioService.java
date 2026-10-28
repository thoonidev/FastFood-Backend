package com.fastfood.auth.service;

import com.fastfood.auth.dto.*;
import com.fastfood.auth.model.Usuario.Rol;
import com.fastfood.auth.model.Usuario.EstadoUsuario;
import com.fastfood.auth.exception.NegocioException;
import com.fastfood.auth.model.Usuario;
import com.fastfood.auth.repository.UsuarioRepository;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.*;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.util.Locale;

@Service
public class UsuarioService {
    private final UsuarioRepository usuarios;
    private final PasswordEncoder passwords;

    public UsuarioService(UsuarioRepository usuarios, PasswordEncoder passwords) {
        this.usuarios = usuarios;
        this.passwords = passwords;
    }

    @Transactional
    public UsuarioResponse registrar(RegistroRequest request, Rol rol) {
        validarPassword(request.getPassword());
        String correo = request.getCorreo().trim().toLowerCase(Locale.ROOT);
        if (usuarios.existsByCorreo(correo)) throw duplicado();
        Usuario usuario = new Usuario();
        usuario.setNombres(request.getNombres().trim());
        usuario.setApellidos(request.getApellidos().trim());
        usuario.setTelefono(request.getTelefono().trim());
        usuario.setCorreo(correo);
        usuario.setPasswordHash(passwords.encode(request.getPassword()));
        usuario.setRol(rol);
        usuario.setEstado(EstadoUsuario.ACTIVO);
        usuario.setVersionSeguridad(1L);
        try {
            return UsuarioResponse.de(usuarios.saveAndFlush(usuario));
        } catch (DataIntegrityViolationException ex) {
            throw duplicado();
        }
    }

    @Transactional(readOnly = true)
    public UsuarioResponse obtener(Long id) {
        return UsuarioResponse.de(usuarios.findById(id).orElseThrow(() -> noEncontrado()));
    }

    @Transactional(readOnly = true)
    public PaginaResponse<UsuarioResponse> listar(
            Rol rol, EstadoUsuario estado, String q, int page, int size) {
        if (page < 0 || size < 1 || size > 100 || (q != null && q.length() > 100))
            throw new NegocioException(
                    400, "VALIDACION", "Los filtros o la paginacion no son validos.");
        Specification<Usuario> filtro = (root, query, cb) -> cb.conjunction();
        if (rol != null) filtro = filtro.and((root, query, cb) -> cb.equal(root.get("rol"), rol));
        if (estado != null)
            filtro = filtro.and((root, query, cb) -> cb.equal(root.get("estado"), estado));
        if (q != null && !q.isBlank()) {
            String patron = "%" + q.trim().toLowerCase(Locale.ROOT) + "%";
            filtro =
                    filtro.and(
                            (root, query, cb) ->
                                    cb.or(
                                            cb.like(cb.lower(root.get("nombres")), patron),
                                            cb.like(cb.lower(root.get("apellidos")), patron),
                                            cb.like(cb.lower(root.get("correo")), patron)));
        }
        return PaginaResponse.de(
                usuarios.findAll(filtro, PageRequest.of(page, size, Sort.by("id")))
                        .map(UsuarioResponse::de));
    }

    @Transactional
    public UsuarioResponse actualizarPerfil(Long id, PerfilRequest request, boolean soloEmpleado) {
        Usuario usuario = bloquear(id, soloEmpleado);
        if (!soloEmpleado && usuario.getEstado() != EstadoUsuario.ACTIVO)
            throw new NegocioException(401, "USUARIO_INACTIVO", "La sesion ya no es valida.");
        usuario.setNombres(request.getNombres().trim());
        usuario.setApellidos(request.getApellidos().trim());
        usuario.setTelefono(request.getTelefono().trim());
        return UsuarioResponse.de(usuarios.saveAndFlush(usuario));
    }

    @Transactional
    public UsuarioResponse cambiarEstado(Long id, EstadoUsuarioRequest request) {
        Usuario usuario = bloquear(id, true);
        if (!usuario.getVersionSeguridad().equals(request.getVersionSeguridadEsperada()))
            throw new NegocioException(
                    409,
                    "VERSION_DESACTUALIZADA",
                    "El empleado fue modificado. Actualiza los datos.");
        if (usuario.getEstado() != request.getEstado()) {
            usuario.setEstado(request.getEstado());
            usuario.setVersionSeguridad(Math.addExact(usuario.getVersionSeguridad(), 1L));
        }
        return UsuarioResponse.de(usuarios.saveAndFlush(usuario));
    }

    @Transactional
    public void crearAdminDemo(String correo, String password) {
        String normalizado = correo.trim().toLowerCase(Locale.ROOT);
        var existente = usuarios.findByCorreo(normalizado);
        if (existente.isPresent()) {
            if (existente.get().getRol() != Rol.ADMINISTRADOR)
                throw new IllegalStateException("La cuenta demo existe con un rol diferente.");
            return;
        }
        registrar(
                new RegistroRequest(
                        "Administrador", "FastFood", normalizado, "999999999", password),
                Rol.ADMINISTRADOR);
    }

    private Usuario bloquear(Long id, boolean empleado) {
        Usuario usuario = usuarios.obtenerConBloqueo(id).orElseThrow(() -> noEncontrado());
        if (empleado && usuario.getRol() != Rol.EMPLEADO) throw noEncontrado();
        return usuario;
    }

    public static void validarPassword(String password) {
        if (password == null
                || password.length() < 8
                || password.getBytes(StandardCharsets.UTF_8).length > 72)
            throw new NegocioException(
                    400,
                    "VALIDACION",
                    "La contraseña debe tener al menos 8 caracteres y como maximo 72 bytes UTF-8.");
    }

    private NegocioException duplicado() {
        return new NegocioException(409, "CORREO_DUPLICADO", "El correo ya esta registrado.");
    }

    private NegocioException noEncontrado() {
        return new NegocioException(
                404, "RECURSO_NO_ENCONTRADO", "El usuario solicitado no existe.");
    }
}
