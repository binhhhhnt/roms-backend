package com.roms.exception;

import lombok.Getter;

@Getter
public class BusinessRuleException extends RuntimeException {

    private final String code;
    private final Object details;

    public BusinessRuleException(String message) {
        super(message);
        this.code = "BUSINESS_RULE_VIOLATION";
        this.details = null;
    }

    public BusinessRuleException(String code, String message) {
        super(message);
        this.code = code;
        this.details = null;
    }

    public BusinessRuleException(String code, String message, Object details) {
        super(message);
        this.code = code;
        this.details = details;
    }
}
