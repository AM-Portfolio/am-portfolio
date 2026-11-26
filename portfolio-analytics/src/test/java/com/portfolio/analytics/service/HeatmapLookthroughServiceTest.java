package com.portfolio.analytics.service;

import com.portfolio.basket.client.EtfApiClient;
import com.portfolio.basket.model.EtfData;
import com.portfolio.basket.model.EtfHolding;
import com.portfolio.basket.service.EnrichedEtfService;
import com.portfolio.model.analytics.lookthrough.HeatmapLookthroughResponse;
import com.portfolio.model.analytics.lookthrough.HeatmapLookthroughResponse.Source;
import com.github.benmanes.caffeine.cache.Cache;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class HeatmapLookthroughServiceTest {

    private static final String SCHEME_MF_ISIN = "INF204KB17I5";
    private static final String ETF_ISIN = "INE591C01017";

    @Mock
    private EnrichedEtfService enrichedEtfService;

    @Mock
    private EtfApiClient etfApiClient;

    private HeatmapLookthroughService service;

    @BeforeEach
    void setUp() {
        service = new HeatmapLookthroughService(enrichedEtfService, etfApiClient, null);
    }

    @Test
    void schemeMfIsin_usesMutualFundLookupAndMarksAvailable() {
        EtfApiClient.EtfApiResponse fund = mock(EtfApiClient.EtfApiResponse.class);
        when(fund.getProductType()).thenReturn("MUTUAL_FUND");
        when(fund.getHoldings()).thenReturn(nonEmptyHoldingsStub());

        EtfApiClient.HoldingsLookupResponse lookup = new EtfApiClient.HoldingsLookupResponse(
                List.of(SCHEME_MF_ISIN),
                1,
                List.of(fund),
                List.of());

        when(etfApiClient.lookupHoldings(eq(List.of(SCHEME_MF_ISIN)), eq(List.of("MUTUAL_FUND"))))
                .thenReturn(lookup);
        when(etfApiClient.toEtfData(fund)).thenReturn(sampleBook("Nippon India Large Cap", "LARGE_CAP"));

        HeatmapLookthroughResponse response = service.lookthrough(SCHEME_MF_ISIN, 50_000.0);

        assertThat(response.isAvailable()).isTrue();
        assertThat(response.getSource()).isEqualTo(Source.MF_HOLDINGS);
        assertThat(response.getSchemeIsin()).isEqualTo(SCHEME_MF_ISIN);
        assertThat(response.getSectors()).isNotEmpty();
        verify(enrichedEtfService, never()).getEnrichedEtf(anyString());
        verify(etfApiClient, never()).lookupHoldings(anyList(), eq(List.of("ETF")));
    }

    @Test
    void schemeMfIsin_emptyMutualFundLookup_unavailable() {
        when(etfApiClient.lookupHoldings(eq(List.of(SCHEME_MF_ISIN)), eq(List.of("MUTUAL_FUND"))))
                .thenReturn(new EtfApiClient.HoldingsLookupResponse(
                        List.of(SCHEME_MF_ISIN), 0, List.of(), List.of(SCHEME_MF_ISIN)));

        HeatmapLookthroughResponse response = service.lookthrough(SCHEME_MF_ISIN, 10_000.0);

        assertThat(response.isAvailable()).isFalse();
        assertThat(response.getSource()).isEqualTo(Source.UNAVAILABLE);
        verify(enrichedEtfService, never()).getEnrichedEtf(anyString());
    }

    @Test
    void nonSchemeIsin_prefersEnrichedEtfPath() {
        EtfData enriched = sampleBook("CPSE ETF", "CPSEETF");
        when(enrichedEtfService.getEnrichedEtf(ETF_ISIN)).thenReturn(enriched);

        HeatmapLookthroughResponse response = service.lookthrough(ETF_ISIN, 25_000.0);

        assertThat(response.isAvailable()).isTrue();
        assertThat(response.getSource()).isEqualTo(Source.ETF_HOLDINGS);
        verify(enrichedEtfService).getEnrichedEtf(ETF_ISIN);
        verify(etfApiClient, never()).lookupHoldings(eq(List.of(ETF_ISIN)), eq(List.of("MUTUAL_FUND")));
    }

    @Test
    void lookthroughCacheEvictsWhenItExceedsConfiguredMaximum() {
        when(enrichedEtfService.getEnrichedEtf(anyString()))
                .thenReturn(sampleBook("Example ETF", "EXAMPLE"));

        for (int i = 0; i < 2_100; i++) {
            service.lookthrough("INE" + String.format("%09d", i), 1_000.0);
        }

        @SuppressWarnings("unchecked")
        Cache<String, ?> cache = (Cache<String, ?>) ReflectionTestUtils.getField(service, "cache");
        cache.cleanUp();
        assertThat(cache.estimatedSize()).isLessThanOrEqualTo(2_000L);
    }

    private static EtfData sampleBook(String schemeName, String symbol) {
        EtfHolding holding = new EtfHolding();
        holding.setIsin("INE467B01029");
        holding.setSymbol("TCS");
        holding.setSector("Information Technology");
        holding.setWeight(12.5);

        EtfData data = new EtfData();
        data.setName(schemeName);
        data.setSymbol(symbol);
        data.setReturnsAsOf("2025-10-31");
        data.setHoldings(List.of(holding));
        return data;
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static List nonEmptyHoldingsStub() {
        return List.of(new Object());
    }
}
