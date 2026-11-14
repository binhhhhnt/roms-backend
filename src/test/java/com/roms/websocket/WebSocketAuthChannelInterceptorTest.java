package com.roms.websocket;

import com.roms.entity.RefreshToken;
import com.roms.entity.RestaurantTable;
import com.roms.entity.ServiceSession;
import com.roms.entity.User;
import com.roms.enums.UserRole;
import com.roms.repository.RefreshTokenRepository;
import com.roms.repository.ServiceSessionRepository;
import com.roms.repository.UserRepository;
import com.roms.security.JwtTokenProvider;
import com.roms.security.UserPrincipal;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class WebSocketAuthChannelInterceptorTest {

    @Mock
    private JwtTokenProvider jwtTokenProvider;

    @Mock
    private UserRepository userRepository;

    @Mock
    private RefreshTokenRepository refreshTokenRepository;

    @Mock
    private ServiceSessionRepository serviceSessionRepository;

    @Mock
    private MessageChannel messageChannel;

    private WebSocketAuthChannelInterceptor interceptor;

    @BeforeEach
    void setUp() {
        interceptor = new WebSocketAuthChannelInterceptor(
                jwtTokenProvider,
                userRepository,
                refreshTokenRepository,
                serviceSessionRepository
        );
    }

    @Test
    @DisplayName("CONNECT with missing Authorization header throws AuthenticationCredentialsNotFoundException")
    void testConnect_missingToken() {
        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.CONNECT);
        Message<?> message = MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());

        assertThatThrownBy(() -> interceptor.preSend(message, messageChannel))
                .isInstanceOf(AuthenticationCredentialsNotFoundException.class)
                .hasMessageContaining("Missing Authorization");
    }

    @Test
    @DisplayName("CONNECT with invalid JWT throws BadCredentialsException")
    void testConnect_invalidJwt() {
        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.CONNECT);
        accessor.setNativeHeader("Authorization", "Bearer invalid-jwt");
        Message<?> message = MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());

        when(jwtTokenProvider.validateToken("invalid-jwt")).thenReturn(false);

        assertThatThrownBy(() -> interceptor.preSend(message, messageChannel))
                .isInstanceOf(BadCredentialsException.class)
                .hasMessageContaining("Invalid or expired JWT");
    }

    @Test
    @DisplayName("CONNECT with valid JWT and active session sets authenticated Principal")
    void testConnect_success() {
        String token = "valid-jwt";
        Long userId = 10L;
        Long sessionId = 100L;

        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.CONNECT);
        accessor.setNativeHeader("Authorization", "Bearer " + token);
        Message<?> message = MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());

        when(jwtTokenProvider.validateToken(token)).thenReturn(true);
        when(jwtTokenProvider.getUserId(token)).thenReturn(userId);
        when(jwtTokenProvider.getSessionId(token)).thenReturn(sessionId);

        User user = User.builder()
                .id(userId)
                .username("chef1")
                .role(UserRole.KITCHEN_STAFF)
                .isActive(true)
                .build();
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));

        RefreshToken session = RefreshToken.builder()
                .id(sessionId)
                .user(user)
                .expiresAt(LocalDateTime.now().plusDays(1))
                .build();
        when(refreshTokenRepository.findByIdAndRevokedAtIsNull(sessionId)).thenReturn(Optional.of(session));

        Message<?> result = interceptor.preSend(message, messageChannel);

        assertThat(result).isNotNull();
        StompHeaderAccessor resultAccessor = StompHeaderAccessor.wrap(result);
        assertThat(resultAccessor.getUser()).isNotNull();
        assertThat(resultAccessor.getUser().getName()).isEqualTo("chef1");
    }

    @Test
    @DisplayName("SUBSCRIBE by KITCHEN_STAFF to /topic/kitchen/orders succeeds")
    void testSubscribe_kitchenStaff_allowed() {
        UserPrincipal principal = UserPrincipal.builder()
                .id(1L)
                .username("cook")
                .role(UserRole.KITCHEN_STAFF)
                .build();
        UsernamePasswordAuthenticationToken auth =
                new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities());

        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.SUBSCRIBE);
        accessor.setDestination("/topic/kitchen/orders");
        accessor.setUser(auth);
        Message<?> message = MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());

        Message<?> result = interceptor.preSend(message, messageChannel);
        assertThat(result).isNotNull();
    }

    @Test
    @DisplayName("SUBSCRIBE by KITCHEN_STAFF to /topic/tables throws AccessDeniedException")
    void testSubscribe_kitchenStaff_deniedTables() {
        UserPrincipal principal = UserPrincipal.builder()
                .id(1L)
                .username("cook")
                .role(UserRole.KITCHEN_STAFF)
                .build();
        UsernamePasswordAuthenticationToken auth =
                new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities());

        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.SUBSCRIBE);
        accessor.setDestination("/topic/tables");
        accessor.setUser(auth);
        Message<?> message = MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());

        assertThatThrownBy(() -> interceptor.preSend(message, messageChannel))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessageContaining("Access denied");
    }

    @Test
    @DisplayName("SUBSCRIBE by QR guest to foreign session throws AccessDeniedException")
    void testSubscribe_qrGuest_mismatchedTable_denied() {
        Long sessionId = 50L;
        UsernamePasswordAuthenticationToken auth =
                new UsernamePasswordAuthenticationToken("qr:token-table-3", null, java.util.List.of());

        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.SUBSCRIBE);
        accessor.setDestination("/topic/qr/session/" + sessionId);
        accessor.setUser(auth);
        Message<?> message = MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());

        RestaurantTable foreignTable = RestaurantTable.builder().id(9L).tableCode("T-09").qrToken("token-table-9").build();
        ServiceSession session = ServiceSession.builder().id(sessionId).table(foreignTable).build();
        when(serviceSessionRepository.findById(sessionId)).thenReturn(Optional.of(session));

        assertThatThrownBy(() -> interceptor.preSend(message, messageChannel))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessageContaining("Access denied");
    }

    @Test
    @DisplayName("SUBSCRIBE by QR guest to own matching session succeeds")
    void testSubscribe_qrGuest_matchingTable_allowed() {
        Long sessionId = 50L;
        UsernamePasswordAuthenticationToken auth =
                new UsernamePasswordAuthenticationToken("qr:token-table-3", null, java.util.List.of());

        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.SUBSCRIBE);
        accessor.setDestination("/topic/qr/session/" + sessionId);
        accessor.setUser(auth);
        Message<?> message = MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());

        RestaurantTable ownTable = RestaurantTable.builder().id(3L).tableCode("T-03").qrToken("token-table-3").build();
        ServiceSession session = ServiceSession.builder().id(sessionId).table(ownTable).build();
        when(serviceSessionRepository.findById(sessionId)).thenReturn(Optional.of(session));

        Message<?> result = interceptor.preSend(message, messageChannel);
        assertThat(result).isNotNull();
    }
}
