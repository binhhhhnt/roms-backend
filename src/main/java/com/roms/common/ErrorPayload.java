package com.roms.common;

import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Builder
public class ErrorPayload {

    public ErrorPayload() {
    }

    public ErrorPayload(String code, Object details) {
        this.code = code;
        this.details = details;
    }

    private String code;
    private Object details;

    public static ErrorPayload of(String code) {
        return new ErrorPayload(code, null);
    }

    public static ErrorPayload of(String code, Object details) {
        return new ErrorPayload(code, details);
    }
}
