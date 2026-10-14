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

    public BusinessRuleException(String arg1, String arg2) {
        super(resolveMessage(arg1, arg2));
        this.code = resolveCode(arg1, arg2);
        this.details = null;
    }

    public BusinessRuleException(String arg1, String arg2, Object details) {
        super(resolveMessage(arg1, arg2));
        this.code = resolveCode(arg1, arg2);
        this.details = details;
    }

    private static boolean isErrorCode(String s) {
        if (s == null || s.isEmpty()) return false;
        return !s.contains(" ") && s.chars().allMatch(c -> Character.isUpperCase(c) || Character.isDigit(c) || c == '_');
    }

    private static String resolveCode(String arg1, String arg2) {
        if (isErrorCode(arg1) && !isErrorCode(arg2)) {
            return arg1;
        }
        if (isErrorCode(arg2) && !isErrorCode(arg1)) {
            return arg2;
        }
        return arg1;
    }

    private static String resolveMessage(String arg1, String arg2) {
        if (isErrorCode(arg1) && !isErrorCode(arg2)) {
            return arg2;
        }
        if (isErrorCode(arg2) && !isErrorCode(arg1)) {
            return arg1;
        }
        return arg2;
    }
}
