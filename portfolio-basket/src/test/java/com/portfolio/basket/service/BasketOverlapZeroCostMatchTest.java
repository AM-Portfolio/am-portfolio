package com.portfolio.basket.service;

import com.portfolio.basket.client.EtfApiClient;
import com.portfolio.basket.engine.overlap.BasketOverlapCalculator;
import com.portfolio.basket.engine.sizing.BasketQuantityCalculator;
import com.portfolio.basket.engine.substitutes.BasketSubstituteApplier;
import com.portfolio.basket.kernel.BasketPortfolioValueCalculator;
import com.portfolio.basket.kernel.BasketPriceResolver;
import com.portfolio.basket.model.BasketOpportunity;
import com.portfolio.basket.model.BasketOpportunity.ItemStatus;
import com.portfolio.basket.model.EtfData;
import com.portfolio.basket.model.EtfHolding;
import com.portfolio.basket.model.OpportunityMode;
import com.portfolio.model.portfolio.EquityHoldings;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anySet;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

/**
 * Dhan-style books: ISIN overlap exists but avg/cost/value are missing → match must still score.
 */
@ExtendWith(MockitoExtension.class)
class BasketOverlapZeroCostMatchTest {

    @Mock private EtfApiClient etfApiClient;
    @Mock private EnrichedEtfService enrichedEtfService;
    @Mock private BasketCatalogService basketCatalogService;
    @Mock private com.portfolio.marketdata.service.MarketDataService marketDataService;
    @Mock private BasketPriceResolver basketPriceResolver;
    @Mock private BasketPortfolioValueCalculator basketPortfolioValueCalculator;

    private BasketEngineService engine;

    @BeforeEach
    void setUp() {
        lenient().when(basketPriceResolver.fetchPricesWithHoldingsFallback(anySet(), anyList()))
                .thenReturn(Collections.emptyMap());
        lenient().when(basketPriceResolver.fetchResolvedPrices(anySet(), anyList()))
                .thenReturn(Collections.emptyMap());
        lenient().when(basketPortfolioValueCalculator.calculate(anyList()))
                .thenReturn(BasketPortfolioValueCalculator.PortfolioValues.builder()
                        .totalPortfolioValue(0.0)
                        .remainingPortfolioValue(0.0)
                        .build());

        BasketOverlapCalculator overlapCalculator = new BasketOverlapCalculator(basketPriceResolver);
        BasketSubstituteApplier substituteApplier =
                new BasketSubstituteApplier(marketDataService, enrichedEtfService, overlapCalculator);
        BasketQuantityCalculator quantityCalculator = new BasketQuantityCalculator(marketDataService);
        engine = new BasketEngineService(
                etfApiClient,
                enrichedEtfService,
                basketCatalogService,
                basketPriceResolver,
                basketPortfolioValueCalculator,
                overlapCalculator,
                substituteApplier,
                quantityCalculator);
    }

    @Test
    void discover_zeroCostHoldings_matchingIsin_countsHeld() {
        EtfData etf = niftyLikeEtf();
        when(enrichedEtfService.getEnrichedEtfsBatch(anyList(), anyBoolean()))
                .thenReturn(Map.of("NIFTYBEES", etf));

        List<EquityHoldings> holdings = List.of(
                EquityHoldings.builder()
                        .isin("INE040A01034")
                        .symbol("HDFCBANK")
                        .quantity(131.0)
                        .investmentCost(0.0)
                        .build(),
                EquityHoldings.builder()
                        .isin("INE467B01029")
                        .symbol("TCS")
                        .quantity(52.0)
                        .build(),
                EquityHoldings.builder()
                        .isin("INE002A01018")
                        .symbol("RELIANCE")
                        .quantity(116.0)
                        .investmentCost(0.0)
                        .build());

        List<BasketOpportunity> opps = engine.findOpportunities(holdings, "NIFTYBEES", OpportunityMode.DISCOVER);
        assertEquals(1, opps.size());
        BasketOpportunity opp = opps.get(0);
        assertTrue(opp.getTotalItems() >= 3);
        assertTrue(opp.getHeldCount() >= 3, "expected heldCount>=3, was " + opp.getHeldCount());
        assertTrue(opp.getMatchScore() > 0, "expected matchScore>0, was " + opp.getMatchScore());
    }

    @Test
    void preview_zeroCostHoldings_hdfcStatusHeld() {
        EtfData etf = niftyLikeEtf();
        when(enrichedEtfService.getEnrichedEtf(org.mockito.ArgumentMatchers.anyString())).thenReturn(etf);

        List<EquityHoldings> holdings = List.of(
                EquityHoldings.builder()
                        .isin("INE040A01034")
                        .symbol("HDFCBANK")
                        .quantity(131.0)
                        .investmentCost(0.0)
                        .build());

        BasketOpportunity opp = engine.getPreview("NIFTYBEES", holdings);
        assertTrue(opp.getHeldCount() >= 1);
        assertTrue(opp.getMatchScore() > 0);
        var hdfc = opp.getComposition().stream()
                .filter(i -> "INE040A01034".equals(i.getIsin()))
                .findFirst()
                .orElseThrow();
        assertEquals(ItemStatus.HELD, hdfc.getStatus());
    }

    @Test
    void discover_availableQuantityZero_doesNotCountHeld() {
        EtfData etf = niftyLikeEtf();
        when(enrichedEtfService.getEnrichedEtfsBatch(anyList(), anyBoolean()))
                .thenReturn(Map.of("NIFTYBEES", etf));

        List<EquityHoldings> holdings = List.of(
                EquityHoldings.builder()
                        .isin("INE040A01034")
                        .symbol("HDFCBANK")
                        .quantity(131.0)
                        .availableQuantity(0.0)
                        .weightInPortfolio(25.0)
                        .investmentCost(10000.0)
                        .build());

        List<BasketOpportunity> opps = engine.findOpportunities(holdings, "NIFTYBEES", OpportunityMode.DISCOVER);
        assertEquals(1, opps.size());
        assertEquals(0, opps.get(0).getHeldCount());
        assertEquals(0.0, opps.get(0).getMatchScore(), 0.01);
    }

    private static EtfData niftyLikeEtf() {
        EtfData etf = new EtfData();
        etf.setSymbol("NIFTYBEES");
        etf.setName("Nifty 50 BeES");
        etf.setHoldings(List.of(
                etfHolding("INE040A01034", "HDFCBANK", 10.0),
                etfHolding("INE467B01029", "TCS", 8.0),
                etfHolding("INE002A01018", "RELIANCE", 9.0),
                etfHolding("INE009A01021", "INFY", 7.0)));
        return etf;
    }

    private static EtfHolding etfHolding(String isin, String symbol, double weight) {
        EtfHolding h = new EtfHolding();
        h.setIsin(isin);
        h.setSymbol(symbol);
        h.setWeight(weight);
        h.setSector("Financial Services");
        return h;
    }
}
