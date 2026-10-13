package com.roms.service;

import java.time.Duration;
import java.util.Optional;

/**
 * Service primitive for managing password-reset OTP and attempt counts in Redis.
 * Key formats:
 *   otp:{email}:hash     -> SHA-256 hash of the OTP (with TTL)
 *   otp:{email}:attempts -> Integer counter tracking verification attempts (with TTL)
 */
public interface OtpRedisService {

    /**
     * Store hashed OTP with default configured TTL.
     *
     * @param email user email address
     * @param hashedOtp SHA-256 hash of the OTP
     */
    void storeOtp(String email, String hashedOtp);

    /**
     * Store hashed OTP with custom TTL.
     *
     * @param email user email address
     * @param hashedOtp SHA-256 hash of the OTP
     * @param ttl time to live duration
     */
    void storeOtp(String email, String hashedOtp, Duration ttl);

    /**
     * Retrieve the stored OTP hash for the given email if present and not expired.
     *
     * @param email user email address
     * @return Optional containing the hashed OTP, or empty if expired or not found
     */
    Optional<String> getOtpHash(String email);

    /**
     * Increment the failed verification attempt count for the given email.
     *
     * @param email user email address
     * @return updated attempt count
     */
    int incrementAttempts(String email);

    /**
     * Get the current failed attempt count for the given email.
     *
     * @param email user email address
     * @return attempt count, or 0 if no attempts recorded
     */
    int getAttempts(String email);

    /**
     * Check if the verification attempts have reached or exceeded the configured maximum.
     *
     * @param email user email address
     * @return true if max attempts exceeded, false otherwise
     */
    boolean isMaxAttemptsReached(String email);

    /**
     * Delete both OTP hash and attempt counter for the given email.
     *
     * @param email user email address
     */
    void deleteOtp(String email);

    /**
     * Get the configured default OTP TTL in seconds.
     *
     * @return default TTL seconds
     */
    long getDefaultTtlSeconds();

    /**
     * Get the configured maximum attempts.
     *
     * @return max attempts count
     */
    int getMaxAttempts();
}
