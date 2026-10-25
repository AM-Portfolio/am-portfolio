package com.portfolio.kafka.publisher;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.UUID;

import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class PortfolioDeleteNotifierAdapterTest {

    @Mock
    private PortfolioEventPublisher portfolioEventPublisher;

    @InjectMocks
    private PortfolioDeleteNotifierAdapter adapter;

    @Test
    void notifyDeleted_delegatesToPublisher() {
        String owner = "owner-" + UUID.randomUUID();
        String portfolioId = UUID.randomUUID().toString();
        String name = "Book-" + UUID.randomUUID().toString().substring(0, 8);

        adapter.notifyDeleted(owner, portfolioId, name, "PORTFOLIO_HTTP_DELETE");

        verify(portfolioEventPublisher)
                .publishPortfolioDelete(owner, portfolioId, name, "PORTFOLIO_HTTP_DELETE");
    }
}
