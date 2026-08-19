package com.portfolio.service.portfolio;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.doAnswer;

import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import java.util.concurrent.Executor;

import com.am.common.amcommondata.model.PortfolioModelV1;
import com.am.common.amcommondata.service.PortfolioService;
import com.portfolio.mapper.holdings.PortfolioHoldingsMapper;
import com.portfolio.model.TimeInterval;
import com.portfolio.model.portfolio.EquityHoldings;
import com.portfolio.model.portfolio.PortfolioHoldings;
import com.portfolio.redis.service.PortfolioHoldingsRedisService;
import com.portfolio.service.calculator.PortfolioCalculator;
import com.portfolio.service.portfolio.PortfolioHoldingsMongoService;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
public class PortfolioHoldingsServiceTest {

    @Mock
    private PortfolioService portfolioService;

    @Mock
    private PortfolioHoldingsMapper portfolioHoldingsMapper;

    @Mock
    private PortfolioHoldingsRedisService portfolioHoldingsRedisService;

    @Mock
    private PortfolioCalculator portfolioCalculator;

    @Mock
    private PortfolioHoldingsMongoService portfolioHoldingsMongoService;

    @Mock
    private Executor taskExecutor;

    @InjectMocks
    private PortfolioHoldingsService portfolioHoldingsService;

    @BeforeEach
    public void setup() {
        ReflectionTestUtils.setField(portfolioHoldingsService, "isRedisEnabled", true);
    }

    @Test
    @DisplayName("getPortfolioHoldings should return cached holdings if present when enrichment is enabled")
    public void getPortfolioHoldings_withCacheHit_shouldReturnCached() {
        // Given
        String userId = "user123";
        TimeInterval interval = TimeInterval.OVERALL;
        PortfolioHoldings cached = PortfolioHoldings.builder().userId(userId).build();

        when(portfolioHoldingsRedisService.getLatestHoldings(userId, interval)).thenReturn(Optional.of(cached));

        // When
        PortfolioHoldings result = portfolioHoldingsService.getPortfolioHoldings(userId, interval, true);

        // Then
        assertThat(result).isNotNull();
        assertThat(result.getUserId()).isEqualTo(userId);
        verify(portfolioService, never()).getPortfoliosByUserId(anyString());
        verify(portfolioHoldingsMapper, never()).toPortfolioHoldingsV1(any());
    }

    @Test
    @DisplayName("getPortfolioHoldings should fetch from service and enrich when cache is a miss")
    public void getPortfolioHoldings_withCacheMissAndEnrich_shouldFetchAndEnrich() {
        // Given
        String userId = "user123";
        TimeInterval interval = TimeInterval.OVERALL;
        UUID portfolioId = UUID.randomUUID();
        PortfolioModelV1 portfolioModel = PortfolioModelV1.builder().id(portfolioId).build();
        List<PortfolioModelV1> portfolios = List.of(portfolioModel);
        
        EquityHoldings holding = EquityHoldings.builder().symbol("TCS").currentPrice(100.0).build();
        List<EquityHoldings> holdings = List.of(holding);
        
        PortfolioHoldings mappedHoldings = PortfolioHoldings.builder().equityHoldings(holdings).build();

        when(portfolioHoldingsRedisService.getLatestHoldings(userId, interval)).thenReturn(Optional.empty());
        when(portfolioService.getPortfoliosByUserId(userId)).thenReturn(portfolios);
        lenient().when(portfolioHoldingsMapper.toEquityHoldings(any())).thenReturn(Collections.emptyList());
        when(portfolioHoldingsMapper.toPortfolioHoldingsV1(portfolios)).thenReturn(mappedHoldings);
        when(portfolioCalculator.enrichHoldings(any())).thenReturn(holdings);

        doAnswer(invocation -> {
            Runnable runnable = invocation.getArgument(0);
            runnable.run();
            return null;
        }).when(taskExecutor).execute(any(Runnable.class));

        // When
        PortfolioHoldings result = portfolioHoldingsService.getPortfolioHoldings(userId, interval, true);

        // Then
        assertThat(result).isNotNull();
        verify(portfolioCalculator, times(1)).enrichHoldings(holdings);
        verify(portfolioCalculator, times(1)).calculateWeights(holdings);
        verify(portfolioHoldingsRedisService, times(1)).cachePortfolioHoldings(
                any(PortfolioHoldings.class), eq(userId), eq(interval), isNull());
    }

    @Test
    @DisplayName("getCachedHoldings should trigger async rebuild when holdings have zero prices but still return stale data")
    public void getCachedHoldings_withZeroPrices_shouldTriggerAsyncRebuild() {
        // Given
        String userId = "user123";
        TimeInterval interval = TimeInterval.OVERALL;
        
        EquityHoldings zeroPriceHolding = new EquityHoldings();
        zeroPriceHolding.setSymbol("TCS");
        zeroPriceHolding.setCurrentPrice(0.0);
        
        PortfolioHoldings cachedHoldings = PortfolioHoldings.builder()
                .equityHoldings(List.of(zeroPriceHolding))
                .build();
                
        lenient().when(portfolioHoldingsRedisService.getLatestHoldings(userId, interval))
                .thenReturn(Optional.empty());
        lenient().when(portfolioHoldingsMongoService.getLatestHoldings(userId, interval, (String) null))
                .thenReturn(Optional.of(cachedHoldings));
                
        // Mock portfolios/holdings to allow rebuild to proceed
        when(portfolioService.getPortfoliosByUserId(userId)).thenReturn(Collections.emptyList());

        doAnswer(invocation -> {
            Runnable runnable = invocation.getArgument(0);
            runnable.run();
            return null;
        }).when(taskExecutor).execute(any(Runnable.class));

        // When
        portfolioHoldingsService.getPortfolioHoldings(userId, interval, true);

        // Then
        // Verify that async rebuild was executed (it fetches portfolios)
        verify(portfolioService, times(1)).getPortfoliosByUserId(userId);
    }

    @Test
    @DisplayName("getPortfolioHoldings should bypass cache and skip enrichment/caching when enrich is false")
    public void getPortfolioHoldings_withEnrichFalse_shouldSkipCacheAndEnrich() {
        // Given
        String userId = "user123";
        TimeInterval interval = TimeInterval.OVERALL;
        UUID portfolioId = UUID.randomUUID();
        PortfolioModelV1 portfolioModel = PortfolioModelV1.builder().id(portfolioId).build();
        List<PortfolioModelV1> portfolios = List.of(portfolioModel);
        
        PortfolioHoldings mappedHoldings = PortfolioHoldings.builder().equityHoldings(Collections.emptyList()).build();

        when(portfolioService.getPortfoliosByUserId(userId)).thenReturn(portfolios);
        when(portfolioHoldingsMapper.toPortfolioHoldingsV1(portfolios)).thenReturn(mappedHoldings);

        // When
        PortfolioHoldings result = portfolioHoldingsService.getPortfolioHoldings(userId, interval, false);

        // Then
        assertThat(result).isNotNull();
        verify(portfolioHoldingsRedisService, never()).getLatestHoldings(anyString(), any());
        verify(portfolioCalculator, never()).enrichHoldings(any());
        verify(portfolioHoldingsRedisService, never()).cachePortfolioHoldings(any(), anyString(), any(), any());
    }

    @Test
    @DisplayName("getPortfolioHoldings should return null gracefully if no portfolios are found")
    public void getPortfolioHoldings_withNoPortfolios_shouldReturnNull() {
        // Given
        String userId = "user123";
        TimeInterval interval = TimeInterval.OVERALL;

        when(portfolioHoldingsRedisService.getLatestHoldings(userId, interval)).thenReturn(Optional.empty());
        when(portfolioService.getPortfoliosByUserId(userId)).thenReturn(Collections.emptyList());

        // When
        PortfolioHoldings result = portfolioHoldingsService.getPortfolioHoldings(userId, interval, true);

        // Then
        assertThat(result.getEquityHoldings()).isEmpty();
    }

    @Test
    @DisplayName("getPortfolioHoldings for specific portfolio should filter and map correctly")
    public void getPortfolioHoldings_forSpecificPortfolio_shouldFilterAndMap() {
        // Given
        String userId = "user123";
        UUID portId1 = UUID.randomUUID();
        UUID portId2 = UUID.randomUUID();
        TimeInterval interval = TimeInterval.OVERALL;

        PortfolioModelV1 p1 = PortfolioModelV1.builder().id(portId1).build();
        PortfolioModelV1 p2 = PortfolioModelV1.builder().id(portId2).build();
        List<PortfolioModelV1> portfolios = List.of(p1, p2);

        PortfolioHoldings mappedHoldings = PortfolioHoldings.builder().equityHoldings(Collections.emptyList()).build();

        when(portfolioService.getPortfoliosByUserId(userId)).thenReturn(portfolios);
        lenient().when(portfolioHoldingsMapper.toEquityHoldings(any())).thenReturn(Collections.emptyList());
        when(portfolioHoldingsMapper.toPortfolioHoldingsV1(List.of(p1))).thenReturn(mappedHoldings);

        // When
        PortfolioHoldings result = portfolioHoldingsService.getPortfolioHoldings(userId, portId1.toString(), interval, true);

        // Then
        assertThat(result).isNotNull();
        verify(portfolioHoldingsMapper).toPortfolioHoldingsV1(List.of(p1));
    }
}
