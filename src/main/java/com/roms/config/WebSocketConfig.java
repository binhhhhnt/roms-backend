package com.roms.config;

import com.roms.websocket.StompDestinations;
import com.roms.websocket.WebSocketAuthChannelInterceptor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.ChannelRegistration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

@Configuration
@EnableWebSocketMessageBroker
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    @Value("${app.cors.allowed-origins:http://localhost:3000,http://localhost:5173}")
    private String allowedOrigins;

    private final WebSocketAuthChannelInterceptor authChannelInterceptor;

    public WebSocketConfig(WebSocketAuthChannelInterceptor authChannelInterceptor) {
        this.authChannelInterceptor = authChannelInterceptor;
    }

    @Override
    public void configureMessageBroker(MessageBrokerRegistry config) {
        // In-memory simple broker for destinations with /topic and /queue prefixes
        config.enableSimpleBroker(StompDestinations.TOPIC_PREFIX, StompDestinations.QUEUE_PREFIX);
        // Application destination prefix for messages routed to @MessageMapping handlers
        config.setApplicationDestinationPrefixes(StompDestinations.APP_PREFIX);
        // User destination prefix for private user-targeted messages
        config.setUserDestinationPrefix(StompDestinations.USER_PREFIX);
    }

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        String[] origins = allowedOrigins.split(",");

        // Native STOMP over WebSocket
        registry.addEndpoint(StompDestinations.WS_ENDPOINT)
                .setAllowedOriginPatterns(origins);

        // SockJS fallback support
        registry.addEndpoint(StompDestinations.WS_ENDPOINT)
                .setAllowedOriginPatterns(origins)
                .withSockJS();
    }

    @Override
    public void configureClientInboundChannel(ChannelRegistration registration) {
        registration.interceptors(authChannelInterceptor);
    }
}
