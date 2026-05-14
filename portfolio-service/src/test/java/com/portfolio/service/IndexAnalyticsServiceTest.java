package com.portfolio.service;

import com.portfolio.marketdata.model.indices.IndexConstituent;
import com.portfolio.marketdata.service.MarketDataService;
import com.portfolio.marketdata.service.NseIndicesService;
import com.portfolio.model.analytics.Heatmap;
import com.portfolio.model.analytics.MarketCapAllocation;
import com.portfolio.model.market.MarketData;
import com.portfolio.model.market.OhlcData;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class IndexAnalyticsServiceTest {

    @Mock
    private NseIndicesService nseIndicesService;

    @Mock
    private MarketDataService marketDataService;

    @InjectMocks
    private IndexAnalyticsService indexAnalyticsService;

    private static final String INDEX_SYMBOL = "NIFTY 50";

    @BeforeEach
    void setUp() {
    }

    @Test
    void getIndexSymbols_Success() {
        IndexConstituent constituent = new IndexConstituent();
        constituent.setSymbol("RELIANCE");
        
        when(nseIndicesService.getIndexConstituents(INDEX_SYMBOL))
                .thenReturn(List.of(constituent));

        List<String> symbols = indexAnalyticsService.getIndexSymbols(INDEX_SYMBOL);

        assertEquals(1, symbols.size());
        assertEquals("RELIANCE", symbols.get(0));
    }

    @Test
    void getIndexSymbols_Empty() {
        when(nseIndicesService.getIndexConstituents(INDEX_SYMBOL))
                .thenReturn(null);

        List<String> symbols = indexAnalyticsService.getIndexSymbols(INDEX_SYMBOL);

        assertTrue(symbols.isEmpty());
    }

    @Test
    void generateSectorHeatmap_Success() {
        // Mock symbols
        IndexConstituent c1 = new IndexConstituent();
        c1.setSymbol("INFY");
        IndexConstituent c2 = new IndexConstituent();
        c2.setSymbol("HDFC");
        
        when(nseIndicesService.getIndexConstituents(INDEX_SYMBOL))
                .thenReturn(List.of(c1, c2));

        // Mock MarketData
        MarketData m1 = MarketData.builder()
                .symbol("INFY")
                .lastPrice(1500.0)
                .ohlc(OhlcData.builder().open(1450.0).close(1480.0).build())
                .build();
        MarketData m2 = MarketData.builder()
                .symbol("HDFC")
                .lastPrice(2500.0)
                .ohlc(OhlcData.builder().open(2550.0).close(2580.0).build())
                .build();

        when(marketDataService.getOhlcData(anyList(), eq(false)))
                .thenReturn(Map.of("INFY", m1, "HDFC", m2));

        Heatmap heatmap = indexAnalyticsService.generateSectorHeatmap(INDEX_SYMBOL);

        assertNotNull(heatmap);
        assertFalse(heatmap.getSectors().isEmpty());
        
        // Verify Information Technology sector (INFY)
        assertTrue(heatmap.getSectors().stream()
                .anyMatch(s -> s.getSectorName().equals("Information Technology")));
        
        // Verify Financial Services sector (HDFC)
        assertTrue(heatmap.getSectors().stream()
                .anyMatch(s -> s.getSectorName().equals("Financial Services")));
    }

    @Test
    void calculateMarketCapAllocations_Success() {
        IndexConstituent c1 = new IndexConstituent();
        c1.setSymbol("RELIANCE"); // Large Cap in mock
        
        when(nseIndicesService.getIndexConstituents(INDEX_SYMBOL))
                .thenReturn(List.of(c1));

        MarketData m1 = MarketData.builder()
                .symbol("RELIANCE")
                .lastPrice(2500.0)
                .instrumentToken(12345L)
                .build();

        when(marketDataService.getOhlcData(anyList(), eq(false)))
                .thenReturn(Map.of("RELIANCE", m1));

        MarketCapAllocation allocation = indexAnalyticsService.calculateMarketCapAllocations(INDEX_SYMBOL);

        assertNotNull(allocation);
        assertFalse(allocation.getSegments().isEmpty());
        
        // RELIANCE is Large Cap (> 50B in mock shares * price)
        // Mock shares for RELIANCE is 8B. 2500 * 8B = 20 Trillion.
        assertTrue(allocation.getSegments().stream()
                .anyMatch(s -> s.getSegmentName().equals("Large Cap")));
    }
}
