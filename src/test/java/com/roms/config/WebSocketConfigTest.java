package com.roms.config;

import com.roms.websocket.StompDestinations;
import com.roms.websocket.WebSocketAuthChannelInterceptor;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.messaging.simp.config.ChannelRegistration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.StompWebSocketEndpointRegistration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class WebSocketConfigTest {

    @Mock
    private WebSocketAuthChannelInterceptor authChannelInterceptor;

    @Mock
    private MessageBrokerRegistry brokerRegistry;

    @Mock
    private StompEndpointRegistry endpointRegistry;

    @Mock
    private StompWebSocketEndpointRegistration endpointRegistration;

    @Mock
    private ChannelRegistration channelRegistration;

    @Test
    @DisplayName("WebSocketConfig configures broker destinations and prefixes correctly")
    void testConfigureMessageBroker() {
        WebSocketConfig config = new WebSocketConfig(authChannelInterceptor);
        config.configureMessageBroker(brokerRegistry);

        verify(brokerRegistry).enableSimpleBroker(StompDestinations.TOPIC_PREFIX, StompDestinations.QUEUE_PREFIX);
        verify(brokerRegistry).setApplicationDestinationPrefixes(StompDestinations.APP_PREFIX);
        verify(brokerRegistry).setUserDestinationPrefix(StompDestinations.USER_PREFIX);
    }

    @Test
    @DisplayName("WebSocketConfig registers STOMP endpoint with CORS and SockJS")
    void testRegisterStompEndpoints() {
        WebSocketConfig config = new WebSocketConfig(authChannelInterceptor);
        ReflectionTestUtils.setField(config, "allowedOrigins", "http://localhost:3000,http://localhost:5173");

        when(endpointRegistry.addEndpoint(StompDestinations.WS_ENDPOINT)).thenReturn(endpointRegistration);
        when(endpointRegistration.setAllowedOriginPatterns(any(String[].class))).thenReturn(endpointRegistration);

        config.registerStompEndpoints(endpointRegistry);

        verify(endpointRegistry, times(2)).addEndpoint(StompDestinations.WS_ENDPOINT);
        verify(endpointRegistration).withSockJS();
    }

    @Test
    @DisplayName("WebSocketConfig registers authChannelInterceptor on inbound channel")
    void testConfigureClientInboundChannel() {
        WebSocketConfig config = new WebSocketConfig(authChannelInterceptor);
        config.configureClientInboundChannel(channelRegistration);

        verify(channelRegistration).interceptors(authChannelInterceptor);
    }
}
