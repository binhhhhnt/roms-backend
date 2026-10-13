package com.roms.websocket;

import com.roms.enums.UserRole;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Shared STOMP destination contracts, paths, and subscription authorization rules.
 */
public final class StompDestinations {

    private StompDestinations() {
        // Prevent instantiation
    }

    public static final String WS_ENDPOINT = "/ws";
    public static final String APP_PREFIX = "/app";
    public static final String TOPIC_PREFIX = "/topic";
    public static final String QUEUE_PREFIX = "/queue";
    public static final String USER_PREFIX = "/user";

    // Standard Destinations
    public static final String TOPIC_KITCHEN_ORDERS = "/topic/kitchen/orders";
    public static final String TOPIC_TABLES = "/topic/tables";
    public static final String TOPIC_SESSION_ORDERS_PATTERN = "^/topic/session/(\\d+)/orders$";
    public static final String USER_NOTIFICATIONS_QUEUE = "/user/queue/notifications";

    private static final Pattern SESSION_ORDERS_REGEX = Pattern.compile(TOPIC_SESSION_ORDERS_PATTERN);

    /**
     * Build destination for kitchen orders topic.
     */
    public static String kitchenOrders() {
        return TOPIC_KITCHEN_ORDERS;
    }

    /**
     * Build destination for a specific session's order topic.
     *
     * @param sessionId active service session id
     * @return destination string e.g. /topic/session/123/orders
     */
    public static String sessionOrders(Long sessionId) {
        if (sessionId == null) {
            throw new IllegalArgumentException("sessionId must not be null");
        }
        return "/topic/session/" + sessionId + "/orders";
    }

    /**
     * Build destination for tables status topic.
     */
    public static String tables() {
        return TOPIC_TABLES;
    }

    /**
     * Extract sessionId from a session orders topic string, or null if not matching.
     *
     * @param destination STOMP destination string
     * @return sessionId Long or null
     */
    public static Long extractSessionId(String destination) {
        if (destination == null) {
            return null;
        }
        Matcher matcher = SESSION_ORDERS_REGEX.matcher(destination.trim());
        if (matcher.matches()) {
            try {
                return Long.parseLong(matcher.group(1));
            } catch (NumberFormatException e) {
                return null;
            }
        }
        return null;
    }

    /**
     * Validate whether a user with the given role and bound tableId is authorized to subscribe to a destination.
     *
     * @param role user's role
     * @param userTableId bound table ID (nullable for staff/owner, present for TABLE_ACCOUNT)
     * @param destination STOMP target destination string
     * @param sessionTableId table ID belonging to the session (required when destination is session-specific)
     * @return true if authorized, false otherwise
     */
    public static boolean isAuthorizedToSubscribe(UserRole role, Long userTableId, String destination, Long sessionTableId) {
        if (role == null || destination == null) {
            return false;
        }

        String dest = destination.trim();

        // OWNER has administrative monitoring access to all topics
        if (role == UserRole.OWNER) {
            return true;
        }

        // Kitchen staff: kitchen topics
        if (dest.equals(TOPIC_KITCHEN_ORDERS) || dest.startsWith("/topic/kitchen/")) {
            return role == UserRole.KITCHEN_STAFF;
        }

        // Tables topic: service staff
        if (dest.equals(TOPIC_TABLES) || dest.startsWith("/topic/tables/")) {
            return role == UserRole.SERVICE_STAFF;
        }

        // Session orders topic: service staff or matching table account
        Long parsedSessionId = extractSessionId(dest);
        if (parsedSessionId != null) {
            if (role == UserRole.SERVICE_STAFF) {
                return true;
            }
            if (role == UserRole.TABLE_ACCOUNT) {
                return userTableId != null && userTableId.equals(sessionTableId);
            }
            return false;
        }

        // User personal notifications queue: any authenticated user
        if (dest.startsWith("/user/") || dest.startsWith("/queue/notifications")) {
            return true;
        }

        return false;
    }
}
