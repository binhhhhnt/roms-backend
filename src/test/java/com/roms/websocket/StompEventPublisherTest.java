package com.roms.websocket;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class StompEventPublisherTest {

    @Mock
    private SimpMessagingTemplate messagingTemplate;

    private StompEventPublisher publisher;

    @BeforeEach
    void setUp() {
        publisher = new StompEventPublisher(messagingTemplate);
    }

    @AfterEach
    void tearDown() {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

    @Test
    @DisplayName("send immediately passes destination and payload to SimpMessagingTemplate")
    void testSendImmediate() {
        String dest = "/topic/kitchen/orders";
        String payload = "test-payload";

        publisher.send(dest, payload);

        verify(messagingTemplate).convertAndSend(dest, payload);
    }

    @Test
    @DisplayName("send handles template exception gracefully without rethrowing")
    void testSendExceptionHandled() {
        doThrow(new RuntimeException("Broker disconnected"))
                .when(messagingTemplate).convertAndSend(anyString(), any(Object.class));

        // Must not throw
        publisher.send("/topic/tables", "payload");
    }

    @Test
    @DisplayName("publishAfterCommit sends immediately when no transaction is active")
    void testPublishAfterCommit_noTransaction() {
        String dest = "/topic/tables";
        String payload = "tables-update";

        publisher.publishAfterCommit(dest, payload);

        verify(messagingTemplate).convertAndSend(dest, payload);
    }

    @Test
    @DisplayName("publishAfterCommit registers synchronization and sends only after commit")
    void testPublishAfterCommit_withTransaction() {
        TransactionSynchronizationManager.initSynchronization();

        String dest = "/topic/kitchen/orders";
        String payload = "new-order";

        publisher.publishAfterCommit(dest, payload);

        // Not sent yet before commit
        verify(messagingTemplate, never()).convertAndSend(anyString(), any(Object.class));

        // Trigger afterCommit
        List<TransactionSynchronization> synchronizations = TransactionSynchronizationManager.getSynchronizations();
        assertThat(synchronizations).hasSize(1);
        synchronizations.get(0).afterCommit();

        // Now it should be sent
        verify(messagingTemplate).convertAndSend(dest, payload);
    }

    @Test
    @DisplayName("publishToUserAfterCommit sends to user queue after transaction commit")
    void testPublishToUserAfterCommit_withTransaction() {
        TransactionSynchronizationManager.initSynchronization();

        String username = "service_waiter";
        String subDest = "/queue/notifications";
        String payload = "order-ready";

        publisher.publishToUserAfterCommit(username, subDest, payload);

        verify(messagingTemplate, never()).convertAndSendToUser(anyString(), anyString(), any(Object.class));

        List<TransactionSynchronization> synchronizations = TransactionSynchronizationManager.getSynchronizations();
        assertThat(synchronizations).hasSize(1);
        synchronizations.get(0).afterCommit();

        verify(messagingTemplate).convertAndSendToUser(eq(username), eq(subDest), eq(payload));
    }
}
