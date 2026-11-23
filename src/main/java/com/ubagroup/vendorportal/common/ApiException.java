package com.ubagroup.vendorportal.common;

/** Erro de regra de negocio / validacao. Devolvido ao browser como JSON {"error": "..."}. */
public class ApiException extends RuntimeException {
    private final int status;

    public ApiException(String message) {
        this(message, 400);
    }

    public ApiException(String message, int status) {
        super(message);
        this.status = status;
    }

    public int getStatus() {
        return status;
    }
}
