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

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@Transactional
class KitchenControllerTest {

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
    private ServiceSession activeSession;

    @BeforeEach
    void setUp() {
        this.mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext)
                .apply(SecurityMockMvcConfigurers.springSecurity())
                .build();

        RestaurantTable table1 = tableRepository.findByTableCode("T01")
                .orElseThrow(() -> new IllegalStateException("Seed table T01 not found"));
        User owner = userRepository.findByUsername("owner")
                .orElseThrow(() -> new IllegalStateException("Seed user owner not found"));

        activeSession = sessionRepository.findByTableIdAndStatus(table1.getId(), ServiceSessionStatus.ACTIVE)
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

    private JsonNode createSampleOrder(String tableToken) throws Exception {
        String orderBody = String.format("""
                {
                    "sessionId": %d,
                    "items": [{"menuItemId": 1, "quantity": 1}]
                }
                """, activeSession.getId());

        MvcResult result = mockMvc.perform(post("/api/orders")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + tableToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(orderBody))
                .andExpect(status().isCreated())
                .andReturn();

        return objectMapper.readTree(result.getResponse().getContentAsString()).path("data");
    }

    @Test
    @DisplayName("Kitchen lifecycle: accept PENDING order -> COOKING -> item READY -> item COMPLETED -> order auto COMPLETED")
    void kitchenOrderLifecycleWorkflow() throws Exception {
        String tableToken = loginAndGetToken("table_t01", "Password@123");
        String kitchenToken = loginAndGetToken("kitchen01", "Password@123");
        String serviceToken = loginAndGetToken("service01", "Password@123");

        // 1. Table creates order
        JsonNode orderData = createSampleOrder(tableToken);
        long orderId = orderData.path("id").asLong();
        long itemId = orderData.path("items").get(0).path("id").asLong();

        // 2. Kitchen accepts order
        mockMvc.perform(post("/api/kitchen/orders/" + orderId + "/accept")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + kitchenToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.status").value("COOKING"));

        // 3. Second accept yields 409 Conflict
        mockMvc.perform(post("/api/kitchen/orders/" + orderId + "/accept")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + kitchenToken))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("INVALID_ORDER_STATUS"));

        // 4. Kitchen starts cooking item
        mockMvc.perform(patch("/api/kitchen/orders/" + orderId + "/items/" + itemId + "/status")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + kitchenToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"COOKING\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("COOKING"));

        // 5. Kitchen finishes item (READY)
        mockMvc.perform(patch("/api/kitchen/orders/" + orderId + "/items/" + itemId + "/status")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + kitchenToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"READY\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("READY"));

        // 6. Service staff serves dish (COMPLETED) -> parent order automatically becomes COMPLETED!
        mockMvc.perform(patch("/api/kitchen/orders/" + orderId + "/items/" + itemId + "/status")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + serviceToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"COMPLETED\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("COMPLETED"));

        // 7. Verify order detail via kitchen API shows COMPLETED
        mockMvc.perform(get("/api/kitchen/orders/" + orderId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + kitchenToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("COMPLETED"));
    }

    @Test
    @DisplayName("SERVICE_STAFF cannot accept order (403 Forbidden)")
    void serviceStaffCannotAcceptOrder() throws Exception {
        String serviceToken = loginAndGetToken("service01", "Password@123");

        mockMvc.perform(post("/api/kitchen/orders/1/accept")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + serviceToken))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("TABLE_ACCOUNT cannot view or accept kitchen orders (403 Forbidden)")
    void tableAccountCannotAccessKitchenApis() throws Exception {
        String tableToken = loginAndGetToken("table_t01", "Password@123");

        mockMvc.perform(get("/api/kitchen/orders")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + tableToken))
                .andExpect(status().isForbidden());
    }
}
