package com.roms.service.impl;

import com.roms.service.MenuContextCacheService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@Component @RequiredArgsConstructor
public class MenuContextInvalidator {
    private final MenuContextCacheService cache;
    public void afterCommit() {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            throw new IllegalStateException("Catalog invalidation requires an active transaction");
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override public void afterCommit() { cache.invalidateMenuContext(); }
        });
    }
}
