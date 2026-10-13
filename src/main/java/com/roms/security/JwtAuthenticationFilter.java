package com.roms.security;

import com.roms.entity.RefreshToken;
import com.roms.entity.User;
import com.roms.repository.RefreshTokenRepository;
import com.roms.repository.UserRepository;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.lang.NonNull;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.Optional;

@Slf4j
@Component
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final String AUTHORIZATION_HEADER = "Authorization";
    private static final String BEARER_PREFIX = "Bearer ";

    private final JwtTokenProvider jwtTokenProvider;
    private final UserRepository userRepository;
    private final RefreshTokenRepository refreshTokenRepository;

    @Override
    protected void doFilterInternal(
            @NonNull HttpServletRequest request,
            @NonNull HttpServletResponse response,
            @NonNull FilterChain filterChain
    ) throws ServletException, IOException {

        String token = extractToken(request);

        if (StringUtils.hasText(token) && jwtTokenProvider.validateToken(token)) {
            try {
                Long userId = jwtTokenProvider.getUserId(token);
                Long sessionId = jwtTokenProvider.getSessionId(token);

                // 1. Verify user exists and is active
                Optional<User> userOpt = userRepository.findById(userId);
                if (userOpt.isPresent() && Boolean.TRUE.equals(userOpt.get().getIsActive())) {
                    User user = userOpt.get();

                    // 2. Verify session in PostgreSQL: must exist, not revoked, and not expired
                    Optional<RefreshToken> sessionOpt = refreshTokenRepository.findByIdAndRevokedAtIsNull(sessionId);
                    if (sessionOpt.isPresent()) {
                        RefreshToken session = sessionOpt.get();
                        if (session.getExpiresAt().isAfter(LocalDateTime.now())) {
                            UserPrincipal principal = UserPrincipal.create(user, sessionId);
                            UsernamePasswordAuthenticationToken authentication =
                                    new UsernamePasswordAuthenticationToken(
                                            principal,
                                            null,
                                            principal.getAuthorities()
                                    );
                            authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                            SecurityContextHolder.getContext().setAuthentication(authentication);
                        } else {
                            log.debug("Session id {} has expired at {}", sessionId, session.getExpiresAt());
                        }
                    } else {
                        log.debug("Session id {} is revoked or not found in PostgreSQL", sessionId);
                    }
                } else {
                    log.debug("User id {} not found or inactive", userId);
                }
            } catch (Exception ex) {
                log.error("Could not set user authentication in security context: {}", ex.getMessage());
            }
        }

        filterChain.doFilter(request, response);
    }

    private String extractToken(HttpServletRequest request) {
        String bearerToken = request.getHeader(AUTHORIZATION_HEADER);
        if (StringUtils.hasText(bearerToken) && bearerToken.startsWith(BEARER_PREFIX)) {
            return bearerToken.substring(BEARER_PREFIX.length()).trim();
        }
        return null;
    }
}
