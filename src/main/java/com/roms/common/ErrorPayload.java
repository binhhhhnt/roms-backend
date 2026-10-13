package com.roms.common;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ErrorPayload {

    private String code;
    private Object details;

    public static ErrorPayload of(String code) {
        return new ErrorPayload(code, null);
    }

    public static ErrorPayload of(String code, Object details) {
        return new ErrorPayload(code, details);
    }
}
