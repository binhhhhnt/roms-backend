package com.roms.service.impl;

import com.roms.service.MenuContextCacheService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Optional;

@Slf4j
@Service
public class MenuContextCacheServiceImpl implements MenuContextCacheService {

    public static final String DEFAULT_MENU_CONTEXT_KEY = "menu:context:v1";

    private final StringRedisTemplate stringRedisTemplate;
    private final String cacheKey;
    private final long defaultTtlSeconds;

    public MenuContextCacheServiceImpl(
            StringRedisTemplate stringRedisTemplate,
            @Value("${app.redis.menu-context-key:" + DEFAULT_MENU_CONTEXT_KEY + "}") String cacheKey,
            @Value("${app.redis.menu-context-ttl-seconds:86400}") long defaultTtlSeconds) {
        this.stringRedisTemplate = stringRedisTemplate;
        this.cacheKey = cacheKey;
        this.defaultTtlSeconds = defaultTtlSeconds;
    }

    @Override
    public void saveMenuContext(String menuContextJson) {
        saveMenuContext(menuContextJson, Duration.ofSeconds(defaultTtlSeconds));
    }

    @Override
    public void saveMenuContext(String menuContextJson, Duration ttl) {
        if (menuContextJson == null) {
            log.warn("Attempted to cache null menu context, skipping cache write");
            return;
        }
        try {
            stringRedisTemplate.opsForValue().set(cacheKey, menuContextJson, ttl);
            log.debug("Successfully saved menu context to Redis key: {} with TTL: {}s", cacheKey, ttl.getSeconds());
        } catch (Exception ex) {
            log.warn("Failed to save menu context to Redis cache key: {}. Error: {}", cacheKey, ex.getMessage());
        }
    }

    @Override
    public Optional<String> getMenuContext() {
        try {
            String cached = stringRedisTemplate.opsForValue().get(cacheKey);
            if (cached != null) {
                log.debug("Menu context cache hit for key: {}", cacheKey);
                return Optional.of(cached);
            }
            log.debug("Menu context cache miss for key: {}", cacheKey);
            return Optional.empty();
        } catch (Exception ex) {
            log.warn("Redis outage or error reading menu context cache key: {}. Returning empty to trigger DB fallback. Error: {}",
                    cacheKey, ex.getMessage());
            return Optional.empty();
        }
    }

    @Override
    public void invalidateMenuContext() {
        try {
            Boolean deleted = stringRedisTemplate.delete(cacheKey);
            log.info("Invalidated menu context cache key: {} (result: {})", cacheKey, deleted);
        } catch (Exception ex) {
            log.warn("Failed to invalidate menu context cache key: {}. Error: {}", cacheKey, ex.getMessage());
        }
    }

    @Override
    public boolean hasMenuContext() {
        try {
            Boolean exists = stringRedisTemplate.hasKey(cacheKey);
            return Boolean.TRUE.equals(exists);
        } catch (Exception ex) {
            log.warn("Redis error checking existence of menu context key: {}. Error: {}", cacheKey, ex.getMessage());
            return false;
        }
    }

    @Override
    public long getDefaultTtlSeconds() {
        return defaultTtlSeconds;
    }
}
