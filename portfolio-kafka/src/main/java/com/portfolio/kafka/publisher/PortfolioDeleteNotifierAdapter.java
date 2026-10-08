package com.portfolio.kafka.publisher;

import com.portfolio.service.event.PortfolioDeleteNotifier;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class PortfolioDeleteNotifierAdapter implements PortfolioDeleteNotifier {

    private final PortfolioEventPublisher portfolioEventPublisher;

    @Override
    public void notifyDeleted(String ownerId, String portfolioId, String name, String source) {
        portfolioEventPublisher.publishPortfolioDelete(ownerId, portfolioId, name, source);
    }
}
