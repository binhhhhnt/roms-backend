package com.roms.service;

import com.roms.service.impl.OtpRedisServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OtpRedisServiceImplTest {

    @Mock
    private StringRedisTemplate stringRedisTemplate;

    @Mock
    private ValueOperations<String, String> valueOperations;

    private OtpRedisServiceImpl otpRedisService;

    private final String email = "staff@roms.com";
    private final String normalizedEmail = "staff@roms.com";
    private final String expectedHashKey = "otp:" + normalizedEmail + ":hash";
    private final String expectedAttemptsKey = "otp:" + normalizedEmail + ":attempts";
    private final long defaultTtl = 300L;
    private final int maxAttempts = 5;

    @BeforeEach
    void setUp() {
        otpRedisService = new OtpRedisServiceImpl(stringRedisTemplate, defaultTtl, maxAttempts);
    }

    @Test
    @DisplayName("storeOtp with default TTL should save hash and delete previous attempts")
    void testStoreOtp_defaultTtl() {
        when(stringRedisTemplate.opsForValue()).thenReturn(valueOperations);

        otpRedisService.storeOtp(email, "hashed_otp_value");

        verify(valueOperations).set(eq(expectedHashKey), eq("hashed_otp_value"), eq(Duration.ofSeconds(defaultTtl)));
        verify(stringRedisTemplate).delete(expectedAttemptsKey);
    }

    @Test
    @DisplayName("storeOtp with custom TTL should save hash and delete previous attempts")
    void testStoreOtp_customTtl() {
        when(stringRedisTemplate.opsForValue()).thenReturn(valueOperations);
        Duration customTtl = Duration.ofSeconds(300);

        otpRedisService.storeOtp(email, "hashed_otp_value", customTtl);

        verify(valueOperations).set(eq(expectedHashKey), eq("hashed_otp_value"), eq(customTtl));
        verify(stringRedisTemplate).delete(expectedAttemptsKey);
    }

    @Test
    @DisplayName("getOtpHash should return optional of hash when present")
    void testGetOtpHash_present() {
        when(stringRedisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get(expectedHashKey)).thenReturn("valid_hash");

        Optional<String> result = otpRedisService.getOtpHash(email);

        assertThat(result).isPresent().contains("valid_hash");
    }

    @Test
    @DisplayName("getOtpHash should return empty when key does not exist or is expired")
    void testGetOtpHash_absent() {
        when(stringRedisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get(expectedHashKey)).thenReturn(null);

        Optional<String> result = otpRedisService.getOtpHash(email);

        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("incrementAttempts on first attempt should set TTL based on remaining OTP TTL")
    void testIncrementAttempts_firstAttempt_inheritsRemainingOtpTtl() {
        when(stringRedisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.increment(expectedAttemptsKey)).thenReturn(1L);
        when(stringRedisTemplate.getExpire(expectedHashKey, TimeUnit.SECONDS)).thenReturn(450L);

        int attempts = otpRedisService.incrementAttempts(email);

        assertThat(attempts).isEqualTo(1);
        verify(stringRedisTemplate).expire(eq(expectedAttemptsKey), eq(Duration.ofSeconds(450)));
    }

    @Test
    @DisplayName("incrementAttempts on first attempt should fall back to default TTL if OTP TTL is expired or missing")
    void testIncrementAttempts_firstAttempt_fallbackDefaultTtl() {
        when(stringRedisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.increment(expectedAttemptsKey)).thenReturn(1L);
        when(stringRedisTemplate.getExpire(expectedHashKey, TimeUnit.SECONDS)).thenReturn(-2L);

        int attempts = otpRedisService.incrementAttempts(email);

        assertThat(attempts).isEqualTo(1);
        verify(stringRedisTemplate).expire(eq(expectedAttemptsKey), eq(Duration.ofSeconds(defaultTtl)));
    }

    @Test
    @DisplayName("incrementAttempts on subsequent attempt should return updated count without resetting TTL")
    void testIncrementAttempts_subsequentAttempt() {
        when(stringRedisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.increment(expectedAttemptsKey)).thenReturn(3L);

        int attempts = otpRedisService.incrementAttempts(email);

        assertThat(attempts).isEqualTo(3);
    }

    @Test
    @DisplayName("getAttempts should return integer count from Redis")
    void testGetAttempts_existing() {
        when(stringRedisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get(expectedAttemptsKey)).thenReturn("2");

        int attempts = otpRedisService.getAttempts(email);

        assertThat(attempts).isEqualTo(2);
    }

    @Test
    @DisplayName("getAttempts should return 0 if key is null")
    void testGetAttempts_null() {
        when(stringRedisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get(expectedAttemptsKey)).thenReturn(null);

        int attempts = otpRedisService.getAttempts(email);

        assertThat(attempts).isEqualTo(0);
    }

    @Test
    @DisplayName("isMaxAttemptsReached should return correct boolean based on configured threshold")
    void testIsMaxAttemptsReached() {
        when(stringRedisTemplate.opsForValue()).thenReturn(valueOperations);

        when(valueOperations.get(expectedAttemptsKey)).thenReturn("4");
        assertThat(otpRedisService.isMaxAttemptsReached(email)).isFalse();

        when(valueOperations.get(expectedAttemptsKey)).thenReturn("5");
        assertThat(otpRedisService.isMaxAttemptsReached(email)).isTrue();

        when(valueOperations.get(expectedAttemptsKey)).thenReturn("6");
        assertThat(otpRedisService.isMaxAttemptsReached(email)).isTrue();
    }

    @Test
    @DisplayName("deleteOtp should delete both hash and attempts keys")
    void testDeleteOtp() {
        otpRedisService.deleteOtp(email);

        verify(stringRedisTemplate).delete(List.of(expectedHashKey, expectedAttemptsKey));
    }
}
