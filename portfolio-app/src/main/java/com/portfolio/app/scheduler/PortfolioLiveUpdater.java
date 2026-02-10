package com.portfolio.app.scheduler;

import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.messaging.simp.user.SimpUserRegistry;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.portfolio.service.portfolio.PortfolioOverviewService;
import com.portfolio.model.TimeInterval;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Component
@RequiredArgsConstructor
@Slf4j
public class PortfolioLiveUpdater {

    private final SimpMessagingTemplate messagingTemplate;
    private final SimpUserRegistry userRegistry;
    private final PortfolioOverviewService portfolioOverviewService;

    // Run every 5 seconds (5000 ms)
    @Scheduled(fixedRate = 5000)
    public void sendPortfolioUpdates() {
        if (userRegistry.getUserCount() == 0) {
            return;
        }

        log.debug("Pushing live updates to {} connected users", userRegistry.getUserCount());

        userRegistry.getUsers().forEach(user -> {
            String userId = user.getName();
            try {
                // Fetch fresh portfolio data (bypassing cache check to ensure live prices)
                var summary = portfolioOverviewService.refreshPortfolio(userId, TimeInterval.ONE_DAY);

                if (summary != null) {
                    messagingTemplate.convertAndSendToUser(userId, "/queue/portfolio", summary);
                }
            } catch (Exception e) {
                log.error("Failed to push update for user: " + userId, e);
            }
        });
    }
}
