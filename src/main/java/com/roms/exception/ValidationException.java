package com.roms.exception;

import lombok.Getter;

@Getter
public class ValidationException extends RuntimeException {

    private final String code;
    private final Object details;

    public ValidationException(String message) {
        super(message);
        this.code = "VALIDATION_ERROR";
        this.details = null;
    }

    public ValidationException(String message, Object details) {
        super(message);
        this.code = "VALIDATION_ERROR";
        this.details = details;
    }

    public ValidationException(String code, String message, Object details) {
        super(message);
        this.code = code;
        this.details = details;
    }
}
