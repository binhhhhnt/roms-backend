package com.roms.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.roms.entity.RestaurantTable;
import com.roms.entity.ServiceSession;
import com.roms.entity.User;
import com.roms.enums.ServiceSessionStatus;
import com.roms.repository.RestaurantTableRepository;
import com.roms.repository.ServiceSessionRepository;
import com.roms.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.WebApplicationContext;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@Transactional
class OrderControllerTest {

    @Autowired
    private WebApplicationContext webApplicationContext;

    @Autowired
    private RestaurantTableRepository tableRepository;

    @Autowired
    private ServiceSessionRepository sessionRepository;

    @Autowired
    private UserRepository userRepository;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private MockMvc mockMvc;
    private ServiceSession activeSessionTable1;

    @BeforeEach
    void setUp() {
        this.mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext)
                .apply(SecurityMockMvcConfigurers.springSecurity())
                .build();

        RestaurantTable table1 = tableRepository.findByTableCode("T01")
                .orElseThrow(() -> new IllegalStateException("Seed table T01 not found"));
        User owner = userRepository.findByUsername("owner")
                .orElseThrow(() -> new IllegalStateException("Seed user owner not found"));

        activeSessionTable1 = sessionRepository.findByTableIdAndStatus(table1.getId(), ServiceSessionStatus.ACTIVE)
                .orElseGet(() -> sessionRepository.save(ServiceSession.builder()
                        .table(table1)
                        .openedBy(owner)
                        .status(ServiceSessionStatus.ACTIVE)
                        .build()));
    }

    private String loginAndGetToken(String username, String password) throws Exception {
        String loginJson = String.format("{\"username\":\"%s\",\"password\":\"%s\"}", username, password);
        MvcResult result = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginJson))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode root = objectMapper.readTree(result.getResponse().getContentAsString());
        return root.path("data").path("accessToken").asText();
    }

    @Test
    @DisplayName("TABLE_ACCOUNT creates order for its own table: returns 201 Created and PENDING status")
    void tableAccountCreatesOrderSuccessfully() throws Exception {
        String token = loginAndGetToken("table_t01", "Password@123");

        String orderBody = String.format("""
                {
                    "sessionId": %d,
                    "items": [
                        {
                            "menuItemId": 1,
                            "quantity": 2,
                            "note": "Không hành tây"
                        }
                    ]
                }
                """, activeSessionTable1.getId());

        mockMvc.perform(post("/api/orders")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(orderBody))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.status").value("PENDING"))
                .andExpect(jsonPath("$.data.items").isArray())
                .andExpect(jsonPath("$.data.items[0].quantity").value(2));
    }

    @Test
    @DisplayName("TABLE_ACCOUNT cannot place order for another table's session (403 Forbidden)")
    void tableAccountCannotOrderForOtherTable() throws Exception {
        RestaurantTable table2 = tableRepository.findByTableCode("T02").orElseThrow();
        User owner = userRepository.findByUsername("owner").orElseThrow();
        ServiceSession sessionTable2 = sessionRepository.findByTableIdAndStatus(table2.getId(), ServiceSessionStatus.ACTIVE)
                .orElseGet(() -> sessionRepository.save(ServiceSession.builder()
                        .table(table2)
                        .openedBy(owner)
                        .status(ServiceSessionStatus.ACTIVE)
                        .build()));

        String token = loginAndGetToken("table_t01", "Password@123"); // Authenticated as table 1

        String orderBody = String.format("""
                {
                    "sessionId": %d,
                    "items": [
                        {
                            "menuItemId": 1,
                            "quantity": 1
                        }
                    ]
                }
                """, sessionTable2.getId()); // Targeting table 2

        mockMvc.perform(post("/api/orders")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(orderBody))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("FORBIDDEN"));
    }

    @Test
    @DisplayName("KITCHEN_STAFF cannot create orders (403 Forbidden)")
    void kitchenStaffCannotCreateOrder() throws Exception {
        String token = loginAndGetToken("kitchen01", "Password@123");

        String orderBody = String.format("""
                {
                    "sessionId": %d,
                    "items": [{"menuItemId": 1, "quantity": 1}]
                }
                """, activeSessionTable1.getId());

        mockMvc.perform(post("/api/orders")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(orderBody))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("Unauthenticated request to create order returns 401 Unauthorized")
    void unauthenticatedCannotCreateOrder() throws Exception {
        String orderBody = """
                {
                    "sessionId": 1,
                    "items": [{"menuItemId": 1, "quantity": 1}]
                }
                """;

        mockMvc.perform(post("/api/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(orderBody))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("UNAUTHORIZED"));
    }

    @Test
    @DisplayName("Cancel PENDING order: returns 200 OK and CANCELLED status; second cancel returns 409 Conflict")
    void cancelPendingOrderWorkflow() throws Exception {
        String token = loginAndGetToken("table_t01", "Password@123");

        // 1. Create order
        String orderBody = String.format("""
                {
                    "sessionId": %d,
                    "items": [{"menuItemId": 1, "quantity": 1}]
                }
                """, activeSessionTable1.getId());

        MvcResult createResult = mockMvc.perform(post("/api/orders")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(orderBody))
                .andExpect(status().isCreated())
                .andReturn();

        JsonNode createNode = objectMapper.readTree(createResult.getResponse().getContentAsString());
        long orderId = createNode.path("data").path("id").asLong();

        // 2. Cancel order
        mockMvc.perform(post("/api/orders/" + orderId + "/cancel")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"Đặt nhầm món\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.status").value("CANCELLED"))
                .andExpect(jsonPath("$.data.terminationReason").value("Đặt nhầm món"));

        // 3. Second cancellation attempts must yield 409 Conflict
        mockMvc.perform(post("/api/orders/" + orderId + "/cancel")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"Hủy tiếp\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("INVALID_ORDER_STATUS"));
    }

    @Test
    @DisplayName("Query orders list: returns 200 OK with list of orders")
    void queryOrdersListSuccessfully() throws Exception {
        String token = loginAndGetToken("service01", "Password@123");

        mockMvc.perform(get("/api/orders")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data").isArray());
    }
}
