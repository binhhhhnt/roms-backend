package com.roms.exception;

import com.roms.common.ApiResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.core.MethodParameter;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.validation.BeanPropertyBindingResult;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @Test
    @DisplayName("Should translate ResourceNotFoundException to 404 with standard envelope")
    void shouldHandleResourceNotFound() {
        ResourceNotFoundException ex = new ResourceNotFoundException("Table with ID 999 not found");
        ResponseEntity<ApiResponse<Void>> response = handler.handleResourceNotFound(ex);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().isSuccess()).isFalse();
        assertThat(response.getBody().getMessage()).isEqualTo("Table with ID 999 not found");
        assertThat(response.getBody().getError().getCode()).isEqualTo("RESOURCE_NOT_FOUND");
    }

    @Test
    @DisplayName("Should translate BusinessRuleException to 409 with custom code")
    void shouldHandleBusinessRuleException() {
        BusinessRuleException ex = new BusinessRuleException(
                "INVALID_ORDER_STATUS",
                "Order cannot be cancelled in COOKING status"
        );
        ResponseEntity<ApiResponse<Void>> response = handler.handleBusinessRuleException(ex);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().isSuccess()).isFalse();
        assertThat(response.getBody().getMessage()).isEqualTo("Order cannot be cancelled in COOKING status");
        assertThat(response.getBody().getError().getCode()).isEqualTo("INVALID_ORDER_STATUS");
    }

    @Test
    @DisplayName("Should translate MethodArgumentNotValidException to 400 with field details")
    void shouldHandleMethodArgumentNotValid() throws Exception {
        Object target = new Object();
        BeanPropertyBindingResult bindingResult = new BeanPropertyBindingResult(target, "createOrderRequest");
        bindingResult.addError(new FieldError("createOrderRequest", "items", "Items list cannot be empty"));
        bindingResult.addError(new FieldError("createOrderRequest", "sessionId", "Session ID is required"));

        MethodParameter parameter = new MethodParameter(
                GlobalExceptionHandlerTest.class.getDeclaredMethod("dummyMethod", String.class),
                0
        );
        MethodArgumentNotValidException ex = new MethodArgumentNotValidException(parameter, bindingResult);

        ResponseEntity<ApiResponse<Void>> response = handler.handleMethodArgumentNotValid(ex);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().isSuccess()).isFalse();
        assertThat(response.getBody().getError().getCode()).isEqualTo("VALIDATION_ERROR");

        @SuppressWarnings("unchecked")
        Map<String, String> details = (Map<String, String>) response.getBody().getError().getDetails();
        assertThat(details).containsEntry("items", "Items list cannot be empty");
        assertThat(details).containsEntry("sessionId", "Session ID is required");
    }

    @Test
    @DisplayName("Should translate AccessDeniedException to 403 Forbidden")
    void shouldHandleAccessDenied() {
        AccessDeniedException ex = new AccessDeniedException("Forbidden");
        ResponseEntity<ApiResponse<Void>> response = handler.handleAccessDenied(ex);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getError().getCode()).isEqualTo("FORBIDDEN");
    }

    @Test
    @DisplayName("Should translate AuthenticationException to 401 Unauthorized")
    void shouldHandleAuthenticationException() {
        AuthenticationException ex = new AuthenticationException("Bad credentials") {};
        ResponseEntity<ApiResponse<Void>> response = handler.handleAuthenticationException(ex);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getError().getCode()).isEqualTo("UNAUTHORIZED");
    }

    @Test
    @DisplayName("Should translate generic Exception to 500 without leaking details")
    void shouldHandleGenericException() {
        Exception ex = new RuntimeException("Database connection timeout or internal secret");
        ResponseEntity<ApiResponse<Void>> response = handler.handleGenericException(ex);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().isSuccess()).isFalse();
        assertThat(response.getBody().getMessage()).isEqualTo("An unexpected internal server error occurred");
        assertThat(response.getBody().getError().getCode()).isEqualTo("INTERNAL_SERVER_ERROR");
        assertThat(response.getBody().getError().getDetails()).isNull();
    }

    @SuppressWarnings("unused")
    private void dummyMethod(String param) {}
}
