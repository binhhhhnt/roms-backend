package com.roms.websocket;

import com.roms.enums.UserRole;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class StompDestinationsTest {

    @Test
    @DisplayName("Destination builders should return correct paths")
    void testDestinationBuilders() {
        assertThat(StompDestinations.kitchenOrders()).isEqualTo("/topic/kitchen/orders");
        assertThat(StompDestinations.tables()).isEqualTo("/topic/tables");
        assertThat(StompDestinations.menu()).isEqualTo("/topic/menu");
        assertThat(StompDestinations.sessionOrders(42L)).isEqualTo("/topic/session/42/orders");

        assertThatThrownBy(() -> StompDestinations.sessionOrders(null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("extractSessionId should parse sessionId from valid destination")
    void testExtractSessionId() {
        assertThat(StompDestinations.extractSessionId("/topic/session/123/orders")).isEqualTo(123L);
        assertThat(StompDestinations.extractSessionId("/topic/session/9999/orders")).isEqualTo(9999L);
        assertThat(StompDestinations.extractSessionId("/topic/kitchen/orders")).isNull();
        assertThat(StompDestinations.extractSessionId("/topic/tables")).isNull();
        assertThat(StompDestinations.extractSessionId(null)).isNull();
        assertThat(StompDestinations.extractSessionId("invalid/path")).isNull();
    }

    @Test
    @DisplayName("OWNER should be authorized to subscribe to all destinations")
    void testOwnerAuthorization() {
        assertThat(StompDestinations.isAuthorizedToSubscribe(UserRole.OWNER, null, "/topic/kitchen/orders", null)).isTrue();
        assertThat(StompDestinations.isAuthorizedToSubscribe(UserRole.OWNER, null, "/topic/tables", null)).isTrue();
        assertThat(StompDestinations.isAuthorizedToSubscribe(UserRole.OWNER, null, "/topic/session/10/orders", 5L)).isTrue();
        assertThat(StompDestinations.isAuthorizedToSubscribe(UserRole.OWNER, null, "/user/queue/notifications", null)).isTrue();
    }

    @Test
    @DisplayName("KITCHEN_STAFF should be authorized only to kitchen topics")
    void testKitchenStaffAuthorization() {
        assertThat(StompDestinations.isAuthorizedToSubscribe(UserRole.KITCHEN_STAFF, null, "/topic/kitchen/orders", null)).isTrue();
        assertThat(StompDestinations.isAuthorizedToSubscribe(UserRole.KITCHEN_STAFF, null, "/topic/tables", null)).isFalse();
        assertThat(StompDestinations.isAuthorizedToSubscribe(UserRole.KITCHEN_STAFF, null, "/topic/session/10/orders", 5L)).isFalse();
    }

    @Test
    @DisplayName("SERVICE_STAFF should be authorized to tables and any session topics, but not kitchen")
    void testServiceStaffAuthorization() {
        assertThat(StompDestinations.isAuthorizedToSubscribe(UserRole.SERVICE_STAFF, null, "/topic/tables", null)).isTrue();
        assertThat(StompDestinations.isAuthorizedToSubscribe(UserRole.SERVICE_STAFF, null, "/topic/session/10/orders", 5L)).isTrue();
        assertThat(StompDestinations.isAuthorizedToSubscribe(UserRole.SERVICE_STAFF, null, "/topic/kitchen/orders", null)).isFalse();
    }

    @Test
    @DisplayName("STAFF_QR_ORDERS and QR_SESSION topics should follow staff/owner authorization")
    void testStaffQrTopicsAuthorization() {
        assertThat(StompDestinations.isAuthorizedToSubscribe(UserRole.SERVICE_STAFF, null, "/topic/staff/qr-orders", null)).isTrue();
        assertThat(StompDestinations.isAuthorizedToSubscribe(UserRole.OWNER, null, "/topic/staff/qr-orders", null)).isTrue();
        assertThat(StompDestinations.isAuthorizedToSubscribe(UserRole.KITCHEN_STAFF, null, "/topic/staff/qr-orders", null)).isFalse();
        assertThat(StompDestinations.isAuthorizedToSubscribe(UserRole.SERVICE_STAFF, null, "/topic/qr/session/10", null)).isTrue();
    }

    @Test
    @DisplayName("INVENTORY_STAFF should not have access to dining/kitchen realtime topics")
    void testInventoryStaffAuthorization() {
        assertThat(StompDestinations.isAuthorizedToSubscribe(UserRole.INVENTORY_STAFF, null, "/topic/kitchen/orders", null)).isFalse();
        assertThat(StompDestinations.isAuthorizedToSubscribe(UserRole.INVENTORY_STAFF, null, "/topic/tables", null)).isFalse();
        assertThat(StompDestinations.isAuthorizedToSubscribe(UserRole.INVENTORY_STAFF, null, "/topic/session/10/orders", 5L)).isFalse();
    }

    @Test
    @DisplayName("Menu topic should be authorized for OWNER, KITCHEN_STAFF, and SERVICE_STAFF")
    void testMenuTopicAuthorization() {
        assertThat(StompDestinations.isAuthorizedToSubscribe(UserRole.OWNER, null, "/topic/menu", null)).isTrue();
        assertThat(StompDestinations.isAuthorizedToSubscribe(UserRole.KITCHEN_STAFF, null, "/topic/menu", null)).isTrue();
        assertThat(StompDestinations.isAuthorizedToSubscribe(UserRole.SERVICE_STAFF, null, "/topic/menu", null)).isTrue();
        assertThat(StompDestinations.isAuthorizedToSubscribe(UserRole.INVENTORY_STAFF, null, "/topic/menu", null)).isFalse();
    }

    @Test
    @DisplayName("Null parameters should be rejected gracefully")
    void testNullParameters() {
        assertThat(StompDestinations.isAuthorizedToSubscribe(null, null, "/topic/tables", null)).isFalse();
        assertThat(StompDestinations.isAuthorizedToSubscribe(UserRole.SERVICE_STAFF, null, null, null)).isFalse();
    }
}
