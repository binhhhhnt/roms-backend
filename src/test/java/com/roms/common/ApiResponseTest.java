package com.roms.common;

import tools.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class ApiResponseTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    @DisplayName("Should serialize success response matching API_CONVENTIONS.md")
    void shouldSerializeSuccessResponse() throws Exception {
        Map<String, Object> orderData = Map.of("id", 101, "status", "PENDING");
        ApiResponse<Map<String, Object>> response = ApiResponse.success("Order created successfully", orderData);

        String json = objectMapper.writeValueAsString(response);

        assertThat(response.isSuccess()).isTrue();
        assertThat(response.getMessage()).isEqualTo("Order created successfully");
        assertThat(response.getData()).isEqualTo(orderData);
        assertThat(response.getError()).isNull();

        // Verify JSON omits error field
        assertThat(json).contains("\"success\":true");
        assertThat(json).contains("\"message\":\"Order created successfully\"");
        assertThat(json).contains("\"data\":{");
        assertThat(json).doesNotContain("\"error\":");
    }

    @Test
    @DisplayName("Should serialize error response matching API_CONVENTIONS.md")
    void shouldSerializeErrorResponse() throws Exception {
        ApiResponse<Void> response = ApiResponse.error(
                "Order cannot be cancelled in its current state",
                "INVALID_ORDER_STATUS",
                null
        );

        String json = objectMapper.writeValueAsString(response);

        assertThat(response.isSuccess()).isFalse();
        assertThat(response.getMessage()).isEqualTo("Order cannot be cancelled in its current state");
        assertThat(response.getError()).isNotNull();
        assertThat(response.getError().getCode()).isEqualTo("INVALID_ORDER_STATUS");
        assertThat(response.getError().getDetails()).isNull();
        assertThat(response.getData()).isNull();

        // Verify JSON omits data field
        assertThat(json).contains("\"success\":false");
        assertThat(json).contains("\"error\":{");
        assertThat(json).contains("\"code\":\"INVALID_ORDER_STATUS\"");
        assertThat(json).doesNotContain("\"data\":");
    }

    @Test
    @DisplayName("Should wrap Spring Data Page into PageResponse matching API_CONVENTIONS.md")
    void shouldWrapPageResponse() throws Exception {
        List<String> items = List.of("Phở Bò", "Cơm Tấm");
        var springPage = new PageImpl<>(items, PageRequest.of(0, 10), 100);

        PageResponse<String> pageResponse = PageResponse.from(springPage);
        ApiResponse<PageResponse<String>> response = ApiResponse.success("Orders retrieved successfully", pageResponse);

        String json = objectMapper.writeValueAsString(response);

        assertThat(pageResponse.getPage()).isEqualTo(0);
        assertThat(pageResponse.getSize()).isEqualTo(10);
        assertThat(pageResponse.getTotalElements()).isEqualTo(100L);
        assertThat(pageResponse.getTotalPages()).isEqualTo(10);
        assertThat(pageResponse.getContent()).containsExactly("Phở Bò", "Cơm Tấm");

        assertThat(json).contains("\"page\":0");
        assertThat(json).contains("\"size\":10");
        assertThat(json).contains("\"totalElements\":100");
        assertThat(json).contains("\"totalPages\":10");
    }

    @Test
    @DisplayName("Should map items in PageResponse via mapper function")
    void shouldMapPageResponseItems() {
        List<Integer> ids = List.of(1, 2, 3);
        var springPage = new PageImpl<>(ids, PageRequest.of(0, 10), 3);

        PageResponse<String> mappedPage = PageResponse.from(springPage, id -> "ID-" + id);

        assertThat(mappedPage.getContent()).containsExactly("ID-1", "ID-2", "ID-3");
    }
}
