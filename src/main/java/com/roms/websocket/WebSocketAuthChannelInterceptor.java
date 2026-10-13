package com.roms.websocket;

import com.roms.entity.RefreshToken;
import com.roms.entity.ServiceSession;
import com.roms.entity.User;
import com.roms.repository.RefreshTokenRepository;
import com.roms.repository.ServiceSessionRepository;
import com.roms.repository.UserRepository;
import com.roms.security.JwtTokenProvider;
import com.roms.security.UserPrincipal;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.security.Principal;
import java.time.LocalDateTime;
import java.util.Optional;

/**
 * Inbound channel interceptor for WebSocket/STOMP.
 * 1. Validates JWT signature, user active status, and PostgreSQL session revocation on CONNECT.
 * 2. Enforces subscription authorization rules based on user role and table binding on SUBSCRIBE.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class WebSocketAuthChannelInterceptor implements ChannelInterceptor {

    private static final String BEARER_PREFIX = "Bearer ";

    private final JwtTokenProvider jwtTokenProvider;
    private final UserRepository userRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final ServiceSessionRepository serviceSessionRepository;

    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        StompHeaderAccessor accessor = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);
        if (accessor == null) {
            return message;
        }

        StompCommand command = accessor.getCommand();

        if (StompCommand.CONNECT.equals(command)) {
            return handleConnect(message, accessor);
        } else if (StompCommand.SUBSCRIBE.equals(command)) {
            handleSubscribe(accessor);
        }

        return message;
    }

    private Message<?> handleConnect(Message<?> message, StompHeaderAccessor accessor) {
        String authHeader = accessor.getFirstNativeHeader("Authorization");
        if (!StringUtils.hasText(authHeader)) {
            authHeader = accessor.getFirstNativeHeader("token");
        }

        if (!StringUtils.hasText(authHeader)) {
            log.warn("STOMP CONNECT rejected: missing Authorization header");
            throw new AuthenticationCredentialsNotFoundException("Missing Authorization token for STOMP connection");
        }

        String token = authHeader.startsWith(BEARER_PREFIX)
                ? authHeader.substring(BEARER_PREFIX.length()).trim()
                : authHeader.trim();

        if (!jwtTokenProvider.validateToken(token)) {
            log.warn("STOMP CONNECT rejected: invalid or expired JWT");
            throw new BadCredentialsException("Invalid or expired JWT token");
        }

        Long userId = jwtTokenProvider.getUserId(token);
        Long sessionId = jwtTokenProvider.getSessionId(token);

        // 1. Verify user exists and is active
        User user = userRepository.findById(userId)
                .filter(u -> Boolean.TRUE.equals(u.getIsActive()))
                .orElseThrow(() -> new BadCredentialsException("User not found or inactive"));

        // 2. Verify session in PostgreSQL: must exist, not revoked, and not expired
        RefreshToken session = refreshTokenRepository.findByIdAndRevokedAtIsNull(sessionId)
                .filter(s -> s.getExpiresAt().isAfter(LocalDateTime.now()))
                .orElseThrow(() -> new BadCredentialsException("Session revoked or expired"));

        UserPrincipal principal = UserPrincipal.create(user, sessionId);
        UsernamePasswordAuthenticationToken authentication =
                new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities());

        log.info("STOMP CONNECT authenticated user: {} (role: {}, tableId: {})",
                principal.getUsername(), principal.getRole(), principal.getTableId());

        if (accessor.isMutable()) {
            accessor.setUser(authentication);
            return message;
        } else {
            StompHeaderAccessor newAccessor = StompHeaderAccessor.create(StompCommand.CONNECT);
            newAccessor.copyHeaders(accessor.getMessageHeaders());
            newAccessor.setUser(authentication);
            return MessageBuilder.createMessage(message.getPayload(), newAccessor.getMessageHeaders());
        }
    }

    private void handleSubscribe(StompHeaderAccessor accessor) {
        Principal user = accessor.getUser();
        if (!(user instanceof UsernamePasswordAuthenticationToken auth) || !(auth.getPrincipal() instanceof UserPrincipal principal)) {
            log.warn("STOMP SUBSCRIBE rejected: unauthenticated user");
            throw new AccessDeniedException("Unauthenticated STOMP subscription");
        }

        String destination = accessor.getDestination();
        if (!StringUtils.hasText(destination)) {
            throw new AccessDeniedException("Destination cannot be empty");
        }

        Long sessionTableId = null;
        Long parsedSessionId = StompDestinations.extractSessionId(destination);
        if (parsedSessionId != null) {
            Optional<ServiceSession> sessionOpt = serviceSessionRepository.findById(parsedSessionId);
            if (sessionOpt.isPresent()) {
                sessionTableId = sessionOpt.get().getTable().getId();
            }
        }

        boolean authorized = StompDestinations.isAuthorizedToSubscribe(
                principal.getRole(),
                principal.getTableId(),
                destination,
                sessionTableId
        );

        if (!authorized) {
            log.warn("STOMP SUBSCRIBE forbidden for user {} (role: {}, tableId: {}) to destination {}",
                    principal.getUsername(), principal.getRole(), principal.getTableId(), destination);
            throw new AccessDeniedException("Access denied for destination: " + destination);
        }

        log.debug("STOMP SUBSCRIBE authorized for user {} to destination {}", principal.getUsername(), destination);
    }
}
