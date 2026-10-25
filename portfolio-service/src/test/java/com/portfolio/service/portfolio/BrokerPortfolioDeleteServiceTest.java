package com.portfolio.service.portfolio;

import com.am.common.amcommondata.model.PortfolioModelV1;
import com.am.common.amcommondata.service.PortfolioService;
import com.portfolio.redis.service.PortfolioHoldingsRedisService;
import com.portfolio.redis.service.PortfolioIntelligenceRedisService;
import com.portfolio.redis.service.PortfolioSummaryRedisService;
import com.portfolio.service.event.PortfolioDeleteNotifier;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.server.ResponseStatusException;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BrokerPortfolioDeleteServiceTest {

    @Mock
    private PortfolioService portfolioService;
    @Mock
    private PortfolioHoldingsRedisService holdingsRedisService;
    @Mock
    private PortfolioSummaryRedisService summaryRedisService;
    @Mock
    private PortfolioIntelligenceRedisService intelligenceRedisService;
    @Mock
    private PortfolioDeleteNotifier deleteNotifier;

    private BrokerPortfolioDeleteService service;

    @BeforeEach
    void setUp() {
        service = new BrokerPortfolioDeleteService(
                portfolioService,
                holdingsRedisService,
                summaryRedisService,
                intelligenceRedisService,
                deleteNotifier);
    }

    @Test
    void deleteOwnedPortfolio_deletesMongoEvictsCachesAndNotifies() {
        UUID portfolioId = UUID.randomUUID();
        String ownerId = "owner-" + UUID.randomUUID();
        String name = "BrokerBook-" + UUID.randomUUID().toString().substring(0, 8);

        PortfolioModelV1 portfolio = new PortfolioModelV1();
        portfolio.setId(portfolioId);
        portfolio.setOwner(ownerId);
        portfolio.setName(name);

        when(portfolioService.getPortfolioById(portfolioId)).thenReturn(portfolio);

        service.deleteOwnedPortfolio(portfolioId.toString(), ownerId);

        verify(portfolioService).deletePortfolioByIdAndOwner(portfolioId.toString(), ownerId);
        verify(holdingsRedisService).evictPortfolioHoldings(ownerId, portfolioId.toString());
        verify(summaryRedisService).evictPortfolioSummary(ownerId, portfolioId.toString());
        verify(holdingsRedisService).evictPortfolioHoldings(ownerId, name);
        verify(summaryRedisService).evictPortfolioSummary(ownerId, name);
        verify(intelligenceRedisService).evict(portfolioId.toString());
        verify(intelligenceRedisService).evictAggregateForUser(ownerId);
        verify(deleteNotifier).notifyDeleted(
                eq(ownerId), eq(portfolioId.toString()), eq(name), eq("PORTFOLIO_HTTP_DELETE"));
    }

    @Test
    void deleteOwnedPortfolio_wrongOwner_forbidden() {
        UUID portfolioId = UUID.randomUUID();
        PortfolioModelV1 portfolio = new PortfolioModelV1();
        portfolio.setId(portfolioId);
        portfolio.setOwner("other-owner");
        when(portfolioService.getPortfolioById(portfolioId)).thenReturn(portfolio);

        ResponseStatusException ex = assertThrows(
                ResponseStatusException.class,
                () -> service.deleteOwnedPortfolio(portfolioId.toString(), "caller-owner"));
        assertEquals(403, ex.getStatusCode().value());
        verify(portfolioService, never()).deletePortfolioByIdAndOwner(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
        verify(deleteNotifier, never()).notifyDeleted(
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any());
    }

    @Test
    void deleteOwnedPortfolio_missing_notFound() {
        UUID portfolioId = UUID.randomUUID();
        when(portfolioService.getPortfolioById(portfolioId)).thenReturn(null);

        ResponseStatusException ex = assertThrows(
                ResponseStatusException.class,
                () -> service.deleteOwnedPortfolio(portfolioId.toString(), "owner-1"));
        assertEquals(404, ex.getStatusCode().value());
    }

    @Test
    void deleteOwnedPortfolio_invalidId_badRequest() {
        ResponseStatusException ex = assertThrows(
                ResponseStatusException.class,
                () -> service.deleteOwnedPortfolio("not-a-uuid", "owner-1"));
        assertEquals(400, ex.getStatusCode().value());
    }

    @Test
    void deleteOwnedPortfolio_blankOwner_unauthorized() {
        ResponseStatusException ex = assertThrows(
                ResponseStatusException.class,
                () -> service.deleteOwnedPortfolio(UUID.randomUUID().toString(), "  "));
        assertEquals(401, ex.getStatusCode().value());
        verify(portfolioService, never()).getPortfolioById(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void deleteOwnedPortfolio_nameEqualsId_skipsDuplicateNameEvict() {
        UUID portfolioId = UUID.randomUUID();
        String ownerId = "owner-" + UUID.randomUUID();
        PortfolioModelV1 portfolio = new PortfolioModelV1();
        portfolio.setId(portfolioId);
        portfolio.setOwner(ownerId);
        portfolio.setName(portfolioId.toString());
        when(portfolioService.getPortfolioById(portfolioId)).thenReturn(portfolio);

        service.deleteOwnedPortfolio(portfolioId.toString(), ownerId);

        verify(holdingsRedisService).evictPortfolioHoldings(ownerId, portfolioId.toString());
        verify(summaryRedisService).evictPortfolioSummary(ownerId, portfolioId.toString());
        // name == id → only one eviction pair
        org.mockito.Mockito.verify(holdingsRedisService, org.mockito.Mockito.times(1))
                .evictPortfolioHoldings(eq(ownerId), eq(portfolioId.toString()));
    }

    @Test
    void deleteOwnedPortfolio_withoutNotifier_stillDeletesMongo() {
        BrokerPortfolioDeleteService noNotifier = new BrokerPortfolioDeleteService(
                portfolioService,
                holdingsRedisService,
                summaryRedisService,
                intelligenceRedisService,
                null);
        UUID portfolioId = UUID.randomUUID();
        String ownerId = "owner-" + UUID.randomUUID();
        PortfolioModelV1 portfolio = new PortfolioModelV1();
        portfolio.setId(portfolioId);
        portfolio.setOwner(ownerId);
        portfolio.setName("Book");
        when(portfolioService.getPortfolioById(portfolioId)).thenReturn(portfolio);

        noNotifier.deleteOwnedPortfolio(portfolioId.toString(), ownerId);

        verify(portfolioService).deletePortfolioByIdAndOwner(portfolioId.toString(), ownerId);
        verify(deleteNotifier, never()).notifyDeleted(
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any());
    }
}
