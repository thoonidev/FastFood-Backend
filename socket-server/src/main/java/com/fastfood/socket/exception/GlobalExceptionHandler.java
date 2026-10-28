package com.fastfood.socket.exception;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.*;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.context.request.*;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

@RestControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {
    @ExceptionHandler(NegocioException.class)
    public ResponseEntity<Map<String, Object>> negocio(NegocioException ex, HttpServletRequest request) {
        Map<String, Object> body = errorBody(ex.getStatus(), ex.getCode(), request.getRequestURI());
        body.put("message", ex.getMessage());
        return ResponseEntity.status(ex.getStatus()).body(body);
    }
    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @Override
    protected ResponseEntity<Object> handleExceptionInternal(Exception ex, Object body,
            HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        String ruta = ((ServletWebRequest) request).getRequest().getRequestURI();
        String codigo = ex instanceof org.springframework.http.converter.HttpMessageNotReadableException
                ? "JSON_INVALIDO" : codigo(status.value());
        Map<String, Object> error = errorBody(status.value(), codigo, ruta);
        if (ex instanceof MethodArgumentNotValidException validacion) {
            error.put("fieldErrors", validacion.getBindingResult().getFieldErrors().stream()
                    .map(campo -> Map.of("field", campo.getField(),
                            "message", java.util.Objects.requireNonNullElse(campo.getDefaultMessage(), "Valor invalido")))
                    .toList());
        }
        return new ResponseEntity<>(error, headers, status);
    }

    @ExceptionHandler({ResourceAccessException.class, HttpServerErrorException.class})
    public ResponseEntity<Map<String, Object>> dependencia(Exception ex, HttpServletRequest request) {
        log.warn("Dependencia no disponible: {} {} ({})", request.getMethod(),
                request.getRequestURI(), ex.getClass().getSimpleName());
        return respuesta(503, request);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, Object>> inesperado(Exception ex, HttpServletRequest request) {
        log.error("Error al atender {} {} ({})", request.getMethod(),
                request.getRequestURI(), ex.getClass().getSimpleName());
        return respuesta(500, request);
    }

    private ResponseEntity<Map<String, Object>> respuesta(int estado, HttpServletRequest request) {
        return ResponseEntity.status(estado).body(errorBody(estado, codigo(estado), request.getRequestURI()));
    }

    // El mismo formato se utiliza en las respuestas de seguridad.
    public static Map<String, Object> errorBody(int estado, String codigo, String ruta) {
        HttpStatus status = HttpStatus.resolve(estado);
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("timestamp", Instant.now().toString());
        body.put("status", estado);
        body.put("error", status == null ? "HTTP_ERROR" : status.name());
        body.put("code", codigo);
        body.put("message", mensaje(estado));
        body.put("path", ruta);
        body.put("fieldErrors", List.of());
        body.put("details", Map.of());
        return body;
    }

    private static String codigo(int estado) {
        return switch (estado) {
            case 400 -> "VALIDACION";
            case 401 -> "AUTENTICACION_REQUERIDA";
            case 403 -> "ACCESO_DENEGADO";
            case 404 -> "RECURSO_NO_ENCONTRADO";
            case 405 -> "METODO_NO_PERMITIDO";
            case 413 -> "MENSAJE_DEMASIADO_GRANDE";
            case 415 -> "FORMATO_NO_COMPATIBLE";
            case 503 -> "DEPENDENCIA_NO_DISPONIBLE";
            default -> "ERROR_INTERNO";
        };
    }

    private static String mensaje(int estado) {
        return switch (estado) {
            case 400 -> "Los datos de la solicitud no son validos.";
            case 401 -> "Se requiere una autenticacion valida.";
            case 403 -> "No tienes permiso para realizar esta operacion.";
            case 404 -> "El recurso solicitado no existe.";
            case 405 -> "El metodo no esta permitido.";
            case 413 -> "La solicitud supera el tamaño permitido.";
            case 415 -> "El formato de la solicitud no es compatible.";
            case 503 -> "El servicio no esta disponible. Consulta el resultado antes de repetir una operacion.";
            default -> "Ocurrio un error interno.";
        };
    }
}
