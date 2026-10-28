package com.fastfood.socket.exception;

import lombok.Getter;

import java.util.Map;

@Getter
public class NegocioException extends RuntimeException {

    private final int status;
    private final String code;
    private final Map<String, Object> details;

    public NegocioException(int status, String code, String message) {
        this(status, code, message, Map.of());
    }

    public NegocioException(int status, String code, String message, Map<String, Object> details) {
        super(message);
        this.status = status;
        this.code = code;
        this.details = details;
    }
}
