package com.roms.service.impl;

import com.roms.service.OtpRedisService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
public class OtpRedisServiceImpl implements OtpRedisService {

    private static final String OTP_KEY_PREFIX = "otp:";
    private static final String HASH_KEY_SUFFIX = ":hash";
    private static final String ATTEMPTS_KEY_SUFFIX = ":attempts";

    private final StringRedisTemplate stringRedisTemplate;
    private final long defaultTtlSeconds;
    private final int maxAttempts;

    public OtpRedisServiceImpl(
            StringRedisTemplate stringRedisTemplate,
            @Value("${app.redis.otp-ttl-seconds:600}") long defaultTtlSeconds,
            @Value("${app.redis.otp-max-attempts:5}") int maxAttempts) {
        this.stringRedisTemplate = stringRedisTemplate;
        this.defaultTtlSeconds = defaultTtlSeconds;
        this.maxAttempts = maxAttempts;
    }

    @Override
    public void storeOtp(String email, String hashedOtp) {
        storeOtp(email, hashedOtp, Duration.ofSeconds(defaultTtlSeconds));
    }

    @Override
    public void storeOtp(String email, String hashedOtp, Duration ttl) {
        String normalizedEmail = normalizeEmail(email);
        String hashKey = buildHashKey(normalizedEmail);
        String attemptsKey = buildAttemptsKey(normalizedEmail);

        stringRedisTemplate.opsForValue().set(hashKey, hashedOtp, ttl);
        stringRedisTemplate.delete(attemptsKey);
        log.debug("Stored OTP hash in Redis for email: {} with TTL: {}s", normalizedEmail, ttl.getSeconds());
    }

    @Override
    public Optional<String> getOtpHash(String email) {
        String normalizedEmail = normalizeEmail(email);
        String hashKey = buildHashKey(normalizedEmail);
        String hash = stringRedisTemplate.opsForValue().get(hashKey);
        return Optional.ofNullable(hash);
    }

    @Override
    public int incrementAttempts(String email) {
        String normalizedEmail = normalizeEmail(email);
        String attemptsKey = buildAttemptsKey(normalizedEmail);
        String hashKey = buildHashKey(normalizedEmail);

        Long currentCount = stringRedisTemplate.opsForValue().increment(attemptsKey);
        int attempts = currentCount != null ? currentCount.intValue() : 1;

        if (attempts == 1) {
            Long remainingTtl = stringRedisTemplate.getExpire(hashKey, TimeUnit.SECONDS);
            if (remainingTtl != null && remainingTtl > 0) {
                stringRedisTemplate.expire(attemptsKey, Duration.ofSeconds(remainingTtl));
            } else {
                stringRedisTemplate.expire(attemptsKey, Duration.ofSeconds(defaultTtlSeconds));
            }
        }

        log.debug("Incremented OTP verification attempts for email: {} to {}", normalizedEmail, attempts);
        return attempts;
    }

    @Override
    public int getAttempts(String email) {
        String normalizedEmail = normalizeEmail(email);
        String attemptsKey = buildAttemptsKey(normalizedEmail);
        String value = stringRedisTemplate.opsForValue().get(attemptsKey);
        if (value == null) {
            return 0;
        }
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException e) {
            log.warn("Invalid integer value found for OTP attempts key {}: {}", attemptsKey, value);
            return 0;
        }
    }

    @Override
    public boolean isMaxAttemptsReached(String email) {
        return getAttempts(email) >= maxAttempts;
    }

    @Override
    public void deleteOtp(String email) {
        String normalizedEmail = normalizeEmail(email);
        String hashKey = buildHashKey(normalizedEmail);
        String attemptsKey = buildAttemptsKey(normalizedEmail);
        stringRedisTemplate.delete(List.of(hashKey, attemptsKey));
        log.debug("Deleted OTP hash and attempts for email: {}", normalizedEmail);
    }

    @Override
    public long getDefaultTtlSeconds() {
        return defaultTtlSeconds;
    }

    @Override
    public int getMaxAttempts() {
        return maxAttempts;
    }

    private String normalizeEmail(String email) {
        return email != null ? email.trim().toLowerCase(Locale.ROOT) : "";
    }

    private String buildHashKey(String email) {
        return OTP_KEY_PREFIX + email + HASH_KEY_SUFFIX;
    }

    private String buildAttemptsKey(String email) {
        return OTP_KEY_PREFIX + email + ATTEMPTS_KEY_SUFFIX;
    }
}
