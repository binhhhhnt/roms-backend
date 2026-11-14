package com.roms.service;

import com.roms.service.impl.MenuContextInvalidator;
import org.junit.jupiter.api.*;
import org.springframework.transaction.support.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class MenuContextInvalidatorTest {
    @AfterEach void cleanup() {
        if(TransactionSynchronizationManager.isSynchronizationActive()) TransactionSynchronizationManager.clearSynchronization();
    }
    @Test @DisplayName("Menu cache: invalidation requires an active transaction")
    void requiresTransaction() {
        var cache = mock(MenuContextCacheService.class);
        var invalidator = new MenuContextInvalidator(cache);
        assertThatThrownBy(invalidator::afterCommit).isInstanceOf(IllegalStateException.class);
        verifyNoInteractions(cache);
    }
    @Test @DisplayName("Menu cache: defer invalidation until the transaction commits")
    void invalidateOnlyAfterCommit() {
        var cache = mock(MenuContextCacheService.class);
        TransactionSynchronizationManager.initSynchronization();
        new MenuContextInvalidator(cache).afterCommit();
        verifyNoInteractions(cache);
        var callbacks = TransactionSynchronizationManager.getSynchronizations();
        assertThat(callbacks).hasSize(1);
        callbacks.getFirst().afterCommit();
        verify(cache).invalidateMenuContext();
    }
    @Test @DisplayName("Menu cache: rollback leaves the existing context untouched")
    void rollbackDoesNotInvalidate() {
        var cache = mock(MenuContextCacheService.class);
        TransactionSynchronizationManager.initSynchronization();
        new MenuContextInvalidator(cache).afterCommit();
        TransactionSynchronizationManager.getSynchronizations().getFirst().afterCompletion(TransactionSynchronization.STATUS_ROLLED_BACK);
        verifyNoInteractions(cache);
    }
}
