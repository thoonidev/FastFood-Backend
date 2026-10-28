package com.fastfood.auth;
import com.fastfood.auth.config.SecurityConfig;
import com.fastfood.auth.security.JwtUtil;
import com.fastfood.auth.repository.UsuarioRepository;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import com.fastfood.auth.exception.*;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.junit.jupiter.api.Test;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(controllers = InfraestructuraRestTests.EntradaController.class)
@Import({SecurityConfig.class, GlobalExceptionHandler.class, JwtUtil.class,
        InfraestructuraRestTests.EntradaController.class})
class InfraestructuraRestTests {
    @MockitoBean UsuarioRepository usuarios;
    @Autowired MockMvc mvc;
    @RestController
    static class EntradaController {
        record Entrada(@NotBlank String correo) {}
        @PostMapping("/api/auth/register")
        String registro(@Valid @RequestBody Entrada entrada) { return "ok"; }
        @PostMapping("/api/auth/login")
        String login() { throw new IllegalStateException("detalle SQL privado"); }
    }
    @Test void validacionInformaCamposSinExponerValores() throws Exception {
        mvc.perform(post("/api/auth/register").contentType("application/json").content("{\"correo\":\"\"}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("VALIDACION"))
                .andExpect(jsonPath("$.fieldErrors[0].field").value("correo"))
                .andExpect(jsonPath("$.details").isMap());
    }
    @Test void jsonMalformadoYCamposExtraSon400() throws Exception {
        for (String body : new String[]{"{", "{\"correo\":\"a@b.com\",\"rol\":\"ADMINISTRADOR\"}"}) {
            mvc.perform(post("/api/auth/register").contentType("application/json").content(body))
                    .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("JSON_INVALIDO"));
        }
    }
    @Test void errorInternoEsGenerico() throws Exception {
        mvc.perform(post("/api/auth/login")).andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.code").value("ERROR_INTERNO"))
                .andExpect(jsonPath("$.message").value("Ocurrio un error interno."))
                .andExpect(jsonPath("$.trace").doesNotExist());
    }
    @Test void apiProtegidaNoAdmiteCabecerasDeIdentidad() throws Exception {
        mvc.perform(get("/api/usuarios").header("X-Rol", "ADMINISTRADOR"))
                .andExpect(status().isUnauthorized()).andExpect(jsonPath("$.code").value("AUTENTICACION_REQUERIDA"))
                .andExpect(header().doesNotExist("Set-Cookie"));
    }
}
