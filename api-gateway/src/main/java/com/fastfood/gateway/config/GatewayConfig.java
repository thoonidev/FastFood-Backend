package com.fastfood.gateway.config;

import com.fastfood.gateway.security.JwtFilter;

import jakarta.servlet.http.*;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.http.client.ClientHttpRequestFactoryBuilder;
import org.springframework.boot.http.client.HttpClientSettings;
import org.springframework.boot.restclient.RestClientCustomizer;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.*;
import org.springframework.core.Ordered;
import org.springframework.http.MediaType;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.cors.*;
import org.springframework.web.filter.CorsFilter;

import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.time.Duration;
import java.util.List;

@Configuration
public class GatewayConfig {

    @Bean
    public RestClient authEstadoClient(
            RestClient.Builder builder,
            @Value("${fastfood.auth.url}") String url,
            ClientHttpRequestFactoryBuilder<?> factory) {
        return builder.baseUrl(url)
                .requestFactory(
                        factory.build(
                                HttpClientSettings.defaults()
                                        .withTimeouts(
                                                Duration.ofSeconds(2), Duration.ofSeconds(3))))
                .build();
    }

    @Bean
    public ClientHttpRequestFactoryBuilder<?> gatewayHttpClient() {
        return ClientHttpRequestFactoryBuilder.httpComponents()
                .withHttpClientCustomizer(client -> client.disableAutomaticRetries());
    }

    @Bean
    public RestClientCustomizer gatewayErrorResponseCustomizer() {
        return builder ->
                builder.requestInterceptor(
                        (request, body, execution) -> {
                            var response = execution.execute(request, body);
                            int status = response.getStatusCode().value();
                            var tipo = response.getHeaders().getContentType();
                            // Conservar los errores JSON de negocio enviados por los servicios.
                            boolean errorJson = tipo != null && MediaType.APPLICATION_JSON.isCompatibleWith(tipo);
                            if (status == 502 || status == 504 || (status == 503 && !errorJson)) {
                                var codigo = response.getStatusCode();
                                response.close();
                                throw new HttpServerErrorException(codigo);
                            }
                            return response;
                        });
    }

    @Bean
    public FilterRegistrationBean<CorsFilter> corsFilterRegistration(
            @Value("${fastfood.cors.allowed-origin}") String origen, JsonMapper mapper) {
        CorsConfiguration cors = new CorsConfiguration();
        cors.setAllowedOrigins(List.of(origen));
        cors.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        cors.setAllowedHeaders(
                List.of("Authorization", "Content-Type", "Accept", "Idempotency-Key"));
        cors.setExposedHeaders(List.of("Location", "Content-Disposition", "Idempotency-Replayed"));
        cors.setAllowCredentials(false);
        cors.setMaxAge(3600L);
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/api/**", cors);
        CorsFilter filtro = new CorsFilter(source);
        filtro.setCorsProcessor(
                new DefaultCorsProcessor() {
                    @Override
                    protected void rejectRequest(
                            org.springframework.http.server.ServerHttpResponse response) {
                        response.setStatusCode(org.springframework.http.HttpStatus.FORBIDDEN);
                    }

                    @Override
                    public boolean processRequest(
                            CorsConfiguration config,
                            HttpServletRequest request,
                            HttpServletResponse response)
                            throws IOException {
                        boolean permitido = super.processRequest(config, request, response);
                        if (!permitido) {
                            response.resetBuffer();
                            JwtFilter.responder(request, response, mapper, 403, "ACCESO_DENEGADO");
                        }
                        return permitido;
                    }
                });
        FilterRegistrationBean<CorsFilter> registration = new FilterRegistrationBean<>(filtro);
        registration.setOrder(Ordered.HIGHEST_PRECEDENCE);
        return registration;
    }
}
