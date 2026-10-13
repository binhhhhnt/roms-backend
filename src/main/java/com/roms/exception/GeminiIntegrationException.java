package com.roms.exception;

public class GeminiIntegrationException extends RuntimeException {
    public GeminiIntegrationException(String message) {
        super(message);
    }

    public GeminiIntegrationException(String message, Throwable cause) {
        super(message, cause);
    }
}
