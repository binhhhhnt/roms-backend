package com.roms.service;

import java.time.Duration;
import java.util.Optional;

/**
 * Service primitive for caching relatively stable Gemini menu context (items, categories, descriptions, recipe context) in Redis.
 * Note: Availability is NOT cached here; availability is always validated live against PostgreSQL.
 * Key format:
 *   menu:context:v1 -> JSON string representation of stable menu context (with TTL)
 */
public interface MenuContextCacheService {

    /**
     * Cache the menu context JSON string with default configured TTL (e.g. 24 hours).
     *
     * @param menuContextJson serialized menu context JSON
     */
    void saveMenuContext(String menuContextJson);

    /**
     * Cache the menu context JSON string with a custom TTL.
     *
     * @param menuContextJson serialized menu context JSON
     * @param ttl time to live duration
     */
    void saveMenuContext(String menuContextJson, Duration ttl);

    /**
     * Retrieve cached menu context JSON if present.
     * If cache misses or Redis is temporarily unavailable, returns Optional.empty()
     * so caller can safely rebuild context from PostgreSQL.
     *
     * @return Optional containing the cached JSON string, or empty if miss/outage
     */
    Optional<String> getMenuContext();

    /**
     * Invalidate (evict) the cached menu context from Redis.
     * Called after committed mutations to menu items, categories, or recipe ingredients.
     */
    void invalidateMenuContext();

    /**
     * Check if valid menu context is currently present in Redis cache.
     *
     * @return true if cache key exists, false otherwise
     */
    boolean hasMenuContext();

    /**
     * Get the configured default cache TTL in seconds.
     *
     * @return default TTL in seconds
     */
    long getDefaultTtlSeconds();
}
