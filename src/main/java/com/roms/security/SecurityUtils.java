package com.roms.security;

import com.roms.enums.UserRole;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.Optional;

public final class SecurityUtils {

    private SecurityUtils() {
    }

    public static Optional<UserPrincipal> getCurrentUserPrincipal() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()) {
            return Optional.empty();
        }
        Object principal = authentication.getPrincipal();
        if (principal instanceof UserPrincipal userPrincipal) {
            return Optional.of(userPrincipal);
        }
        return Optional.empty();
    }

    public static Optional<Long> getCurrentUserId() {
        return getCurrentUserPrincipal().map(UserPrincipal::getId);
    }

    public static Optional<UserRole> getCurrentUserRole() {
        return getCurrentUserPrincipal().map(UserPrincipal::getRole);
    }

    public static Optional<Long> getCurrentSessionId() {
        return getCurrentUserPrincipal().map(UserPrincipal::getSessionId);
    }

    public static Optional<Long> getCurrentTableId() {
        return getCurrentUserPrincipal().map(UserPrincipal::getTableId);
    }
}
