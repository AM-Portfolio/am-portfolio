package com.portfolio.analytics.intelligence;

import com.am.common.amcommondata.model.PortfolioModelV1;
import com.am.common.amcommondata.service.PortfolioService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AggregatePortfolioLoaderTest {

    @Mock
    private PortfolioService portfolioService;

    @Mock
    private AggregatePortfolioFactory aggregatePortfolioFactory;

    private AggregatePortfolioLoader loader;

    @BeforeEach
    void setUp() {
        loader = new AggregatePortfolioLoader(portfolioService, aggregatePortfolioFactory);
    }

    @Test
    void loadMerged_cachesWithinTtl() {
        String userId = "user-1";
        PortfolioModelV1 book = new PortfolioModelV1();
        book.setId(UUID.randomUUID());
        when(portfolioService.getPortfoliosByUserId(userId)).thenReturn(List.of(book));
        when(aggregatePortfolioFactory.mergeBrokerBooks(eq(userId), anyList())).thenReturn(book);

        PortfolioModelV1 first = loader.loadMerged(userId);
        PortfolioModelV1 second = loader.loadMerged(userId);

        assertThat(first).isSameAs(book);
        assertThat(second).isSameAs(book);
        verify(portfolioService, times(1)).getPortfoliosByUserId(userId);
        verify(aggregatePortfolioFactory, times(1)).mergeBrokerBooks(eq(userId), anyList());
    }

    @Test
    void evict_forcesReload() {
        String userId = "user-2";
        PortfolioModelV1 book = new PortfolioModelV1();
        book.setId(UUID.randomUUID());
        when(portfolioService.getPortfoliosByUserId(userId)).thenReturn(List.of(book));
        when(aggregatePortfolioFactory.mergeBrokerBooks(eq(userId), anyList())).thenReturn(book);

        loader.loadMerged(userId);
        loader.evict(userId);
        loader.loadMerged(userId);

        verify(portfolioService, times(2)).getPortfoliosByUserId(userId);
    }
}
