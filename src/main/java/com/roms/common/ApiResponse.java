package com.roms.common;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Builder
public class ApiResponse<T> {

    public ApiResponse() {
    }

    public ApiResponse(boolean success, String message, T data, ErrorPayload error) {
        this.success = success;
        this.message = message;
        this.data = data;
        this.error = error;
    }

    private boolean success;
    private String message;

    @JsonInclude(JsonInclude.Include.NON_NULL)
    private T data;

    @JsonInclude(JsonInclude.Include.NON_NULL)
    private ErrorPayload error;

    public static <T> ApiResponse<T> success(T data) {
        return ApiResponse.<T>builder()
                .success(true)
                .message("Success")
                .data(data)
                .build();
    }

    public static <T> ApiResponse<T> success(String message, T data) {
        return ApiResponse.<T>builder()
                .success(true)
                .message(message)
                .data(data)
                .build();
    }

    public static <T> ApiResponse<T> error(String message, String code) {
        return ApiResponse.<T>builder()
                .success(false)
                .message(message)
                .error(ErrorPayload.of(code))
                .build();
    }

    public static <T> ApiResponse<T> error(String message, String code, Object details) {
        return ApiResponse.<T>builder()
                .success(false)
                .message(message)
                .error(ErrorPayload.of(code, details))
                .build();
    }

    public static <T> ApiResponse<T> error(String message, ErrorPayload error) {
        return ApiResponse.<T>builder()
                .success(false)
                .message(message)
                .error(error)
                .build();
    }
}
