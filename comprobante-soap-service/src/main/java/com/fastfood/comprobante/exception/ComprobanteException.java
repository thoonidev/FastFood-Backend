package com.fastfood.comprobante.exception;

import lombok.Getter;

@Getter
public class ComprobanteException extends RuntimeException {
    private final String code;

    public ComprobanteException(String code, String message) {
        super(message);
        this.code = code;
    }
}
