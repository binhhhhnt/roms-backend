package com.roms.websocket;

import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * Utility for publishing STOMP WebSocket messages.
 * Critical architecture rule: events must be published AFTER database commit.
 */
@Slf4j
@Component
public class StompEventPublisher {

    private final SimpMessagingTemplate messagingTemplate;

    public StompEventPublisher(SimpMessagingTemplate messagingTemplate) {
        this.messagingTemplate = messagingTemplate;
    }

    /**
     * Publish an event to a STOMP destination AFTER the current database
     * transaction commits successfully.
     * If no active transaction synchronization is present, sends the message
     * immediately.
     *
     * @param destination target STOMP destination (e.g., /topic/kitchen/orders)
     * @param payload     event object to serialize and send
     */
    public void publishAfterCommit(String destination, Object payload) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    send(destination, payload);
                }
            });
            log.debug("Registered after-commit STOMP event publisher for destination: {}", destination);
        } else {
            send(destination, payload);
        }
    }

    /**
     * Send a STOMP message immediately.
     *
     * @param destination target STOMP destination
     * @param payload     event object to serialize and send
     */
    public void send(String destination, Object payload) {
        try {
            messagingTemplate.convertAndSend(destination, payload);
            log.debug("Published STOMP event to destination: {}", destination);
        } catch (Exception ex) {
            log.error("Failed to publish STOMP event to destination {}: {}", destination, ex.getMessage(), ex);
        }
    }

    /**
     * Publish a private event to a specific user after transaction commit.
     *
     * @param username    target username
     * @param destination sub-destination (e.g., /queue/notifications)
     * @param payload     event object
     */
    public void publishToUserAfterCommit(String username, String destination, Object payload) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    sendToUser(username, destination, payload);
                }
            });
            log.debug("Registered after-commit user-targeted STOMP publisher for user: {}", username);
        } else {
            sendToUser(username, destination, payload);
        }
    }

    /**
     * Send a private event to a specific user immediately.
     *
     * @param username    target username
     * @param destination sub-destination
     * @param payload     event object
     */
    public void sendToUser(String username, String destination, Object payload) {
        try {
            messagingTemplate.convertAndSendToUser(username, destination, payload);
            log.debug("Published STOMP event to user {} on destination: {}", username, destination);
        } catch (Exception ex) {
            log.error("Failed to publish STOMP event to user {} on destination {}: {}", username, destination,
                    ex.getMessage(), ex);
        }
    }
}
