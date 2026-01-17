package com.portfolio.app.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

@Configuration
@EnableWebSocketMessageBroker
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    @Override
    public void configureMessageBroker(MessageBrokerRegistry config) {
        // Enable simple memory-based message broker to proxy messages back to the
        // client
        // on destinations prefixed with /topic and /queue
        config.enableSimpleBroker("/topic", "/queue");

        // Designate prefix for messages bound for methods annotated with
        // @MessageMapping
        config.setApplicationDestinationPrefixes("/app");

        // Prefix for user-specific messages
        config.setUserDestinationPrefix("/user");
    }

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        // Register the "/ws-portfolio" endpoint, enabling SockJS fallback options
        registry.addEndpoint("/ws-portfolio")
                .setAllowedOriginPatterns("*") // Use Patterns for flexibility with CORS
                .withSockJS();
    }
}
