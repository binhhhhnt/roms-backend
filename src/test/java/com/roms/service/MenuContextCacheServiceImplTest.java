package com.roms.service;

import com.roms.service.impl.MenuContextCacheServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Duration;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MenuContextCacheServiceImplTest {

    @Mock
    private StringRedisTemplate stringRedisTemplate;

    @Mock
    private ValueOperations<String, String> valueOperations;

    private MenuContextCacheServiceImpl menuContextCacheService;

    private final String cacheKey = "menu:context:v1";
    private final long defaultTtl = 86400L;
    private final String sampleJson = "{\"items\":[{\"id\":1,\"name\":\"Pho Bo\"}]}";

    @BeforeEach
    void setUp() {
        menuContextCacheService = new MenuContextCacheServiceImpl(stringRedisTemplate, cacheKey, defaultTtl);
    }

    @Test
    @DisplayName("saveMenuContext with default TTL saves context to Redis")
    void testSaveMenuContext_defaultTtl() {
        when(stringRedisTemplate.opsForValue()).thenReturn(valueOperations);

        menuContextCacheService.saveMenuContext(sampleJson);

        verify(valueOperations).set(eq(cacheKey), eq(sampleJson), eq(Duration.ofSeconds(defaultTtl)));
    }

    @Test
    @DisplayName("saveMenuContext with custom TTL saves context to Redis")
    void testSaveMenuContext_customTtl() {
        when(stringRedisTemplate.opsForValue()).thenReturn(valueOperations);
        Duration customTtl = Duration.ofHours(12);

        menuContextCacheService.saveMenuContext(sampleJson, customTtl);

        verify(valueOperations).set(eq(cacheKey), eq(sampleJson), eq(customTtl));
    }

    @Test
    @DisplayName("saveMenuContext with null string should skip write safely")
    void testSaveMenuContext_nullValue() {
        menuContextCacheService.saveMenuContext(null);

        verify(stringRedisTemplate, never()).opsForValue();
    }

    @Test
    @DisplayName("saveMenuContext handles Redis exception gracefully without throwing")
    void testSaveMenuContext_redisOutage() {
        when(stringRedisTemplate.opsForValue()).thenReturn(valueOperations);
        doThrow(new RedisConnectionFailureException("Connection refused"))
                .when(valueOperations).set(any(), any(), any(Duration.class));

        // Must not throw exception
        menuContextCacheService.saveMenuContext(sampleJson);
    }

    @Test
    @DisplayName("getMenuContext returns cached JSON on cache hit")
    void testGetMenuContext_hit() {
        when(stringRedisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get(cacheKey)).thenReturn(sampleJson);

        Optional<String> result = menuContextCacheService.getMenuContext();

        assertThat(result).isPresent().contains(sampleJson);
    }

    @Test
    @DisplayName("getMenuContext returns empty on cache miss")
    void testGetMenuContext_miss() {
        when(stringRedisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get(cacheKey)).thenReturn(null);

        Optional<String> result = menuContextCacheService.getMenuContext();

        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("getMenuContext returns empty on Redis outage so caller can safely fall back to DB")
    void testGetMenuContext_redisOutage() {
        when(stringRedisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get(cacheKey)).thenThrow(new RedisConnectionFailureException("Connection refused"));

        Optional<String> result = menuContextCacheService.getMenuContext();

        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("invalidateMenuContext deletes the cache key")
    void testInvalidateMenuContext() {
        when(stringRedisTemplate.delete(cacheKey)).thenReturn(Boolean.TRUE);

        menuContextCacheService.invalidateMenuContext();

        verify(stringRedisTemplate).delete(cacheKey);
    }

    @Test
    @DisplayName("invalidateMenuContext handles Redis outage gracefully without throwing")
    void testInvalidateMenuContext_redisOutage() {
        when(stringRedisTemplate.delete(cacheKey)).thenThrow(new RedisConnectionFailureException("Connection refused"));

        // Must not throw exception
        menuContextCacheService.invalidateMenuContext();
    }

    @Test
    @DisplayName("hasMenuContext returns true when key exists")
    void testHasMenuContext_exists() {
        when(stringRedisTemplate.hasKey(cacheKey)).thenReturn(Boolean.TRUE);

        assertThat(menuContextCacheService.hasMenuContext()).isTrue();
    }

    @Test
    @DisplayName("hasMenuContext returns false when key missing or Redis exception occurs")
    void testHasMenuContext_missingOrError() {
        when(stringRedisTemplate.hasKey(cacheKey)).thenReturn(Boolean.FALSE);
        assertThat(menuContextCacheService.hasMenuContext()).isFalse();

        when(stringRedisTemplate.hasKey(cacheKey)).thenThrow(new RedisConnectionFailureException("Error"));
        assertThat(menuContextCacheService.hasMenuContext()).isFalse();
    }
}
