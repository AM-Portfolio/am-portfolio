package com.portfolio.app.controller;

import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.messaging.simp.annotation.SubscribeMapping;
import org.springframework.stereotype.Controller;

import lombok.extern.slf4j.Slf4j;

@Controller
@Slf4j
public class PortfolioWebSocketController {

    @SuppressWarnings("unused")
    private final SimpMessagingTemplate messagingTemplate;

    public PortfolioWebSocketController(SimpMessagingTemplate messagingTemplate) {
        this.messagingTemplate = messagingTemplate;
    }

    /**
     * Endpoint for clients to subscribe to their portfolio updates.
     * This is largely handled by the broker, but we can hook in here if needed.
     */
    @SubscribeMapping("/topic/portfolio")
    public void subscribeToPortfolio() {
        log.info("Client subscribed to portfolio updates");
    }
}
