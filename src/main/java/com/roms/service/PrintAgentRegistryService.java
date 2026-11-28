package com.roms.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

@Service
public class PrintAgentRegistryService {

    private static final long ONLINE_TTL_SECONDS = 75;

    private final String configuredKey;
    private final AtomicReference<List<String>> printers = new AtomicReference<>(Collections.emptyList());
    private final AtomicReference<Instant> lastSeenAt = new AtomicReference<>(null);

    public PrintAgentRegistryService(@Value("${app.print-agent.key:}") String configuredKey) {
        this.configuredKey = configuredKey != null ? configuredKey.trim() : "";
    }

    public boolean isValidKey(String providedKey) {
        if (!StringUtils.hasText(configuredKey) || !StringUtils.hasText(providedKey)) {
            return false;
        }
        return MessageDigest.isEqual(
                configuredKey.getBytes(StandardCharsets.UTF_8),
                providedKey.trim().getBytes(StandardCharsets.UTF_8)
        );
    }

    public void updatePrinters(List<String> printerNames) {
        List<String> sanitized = printerNames == null
                ? Collections.emptyList()
                : printerNames.stream()
                        .filter(StringUtils::hasText)
                        .map(String::trim)
                        .distinct()
                        .toList();
        printers.set(sanitized);
        lastSeenAt.set(Instant.now());
    }

    public boolean isOnline() {
        Instant seen = lastSeenAt.get();
        return seen != null && seen.isAfter(Instant.now().minusSeconds(ONLINE_TTL_SECONDS));
    }

    public Instant getLastSeenAt() {
        return lastSeenAt.get();
    }

    public List<String> getPrinters() {
        return isOnline() ? printers.get() : Collections.emptyList();
    }
}
