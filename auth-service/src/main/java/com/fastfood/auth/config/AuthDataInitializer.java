package com.fastfood.auth.config;

import com.fastfood.auth.service.UsuarioService;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "fastfood.demo.enabled", havingValue = "true")
public class AuthDataInitializer implements CommandLineRunner {
    private final UsuarioService usuarios;
    private final String correo;
    private final String password;

    public AuthDataInitializer(
            UsuarioService usuarios,
            @Value("${fastfood.demo.admin.correo}") String correo,
            @Value("${fastfood.demo.admin.password}") String password) {
        this.usuarios = usuarios;
        this.correo = correo;
        this.password = password;
    }

    @Override
    public void run(String... args) {
        if (password == null || password.isBlank()) {
            throw new IllegalStateException(
                    "Configura DEMO_ADMIN_PASSWORD para activar el administrador de demostracion");
        }
        usuarios.crearAdminDemo(correo, password);
    }
}
