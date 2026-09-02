package com.portfolio.basket.service;

import com.portfolio.basket.client.EtfApiClient;
import com.portfolio.basket.engine.overlap.BasketOverlapCalculator;
import com.portfolio.basket.engine.sizing.BasketQuantityCalculator;
import com.portfolio.basket.engine.substitutes.BasketSubstituteApplier;
import com.portfolio.basket.kernel.BasketPortfolioValueCalculator;
import com.portfolio.basket.kernel.BasketPriceResolver;
import com.portfolio.basket.model.BasketOpportunity;
import com.portfolio.basket.model.BasketOpportunity.BasketItem;
import com.portfolio.basket.model.BasketOpportunity.ItemStatus;
import com.portfolio.basket.model.EtfData;
import com.portfolio.basket.model.EtfHolding;
import com.portfolio.basket.util.BasketUtils;
import com.portfolio.marketdata.service.MarketDataService;
import com.portfolio.model.portfolio.EquityHoldings;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anySet;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
public class BasketEngineServiceTest {

    @Mock
    private EtfApiClient etfApiClient;

    @Mock
    private EnrichedEtfService enrichedEtfService;

    @Mock
    private BasketCatalogService basketCatalogService;

    @Mock
    private MarketDataService marketDataService;

    @Mock
    private BasketPriceResolver basketPriceResolver;

    @Mock
    private BasketPortfolioValueCalculator basketPortfolioValueCalculator;

    private BasketOverlapCalculator overlapCalculator;
    private BasketSubstituteApplier substituteApplier;
    private BasketQuantityCalculator quantityCalculator;
    private BasketEngineService basketEngineService;

    private BasketOpportunity opportunity;
    private Map<String, Double> prices;

    @BeforeEach
    void setUp() {
        prices = new HashMap<>();
        prices.put("AAPL", 150.0);
        prices.put("GOOGL", 2500.0);

        lenient().when(basketPriceResolver.fetchPricesWithHoldingsFallback(anySet(), anyList()))
                .thenAnswer(inv -> prices);
        lenient().when(basketPortfolioValueCalculator.calculate(anyList()))
                .thenReturn(BasketPortfolioValueCalculator.PortfolioValues.builder()
                        .totalPortfolioValue(1000.0)
                        .remainingPortfolioValue(800.0)
                        .build());

        overlapCalculator = new BasketOverlapCalculator(basketPriceResolver);
        substituteApplier = new BasketSubstituteApplier(marketDataService, enrichedEtfService, overlapCalculator);
        quantityCalculator = new BasketQuantityCalculator(marketDataService);
        basketEngineService = new BasketEngineService(
                etfApiClient,
                enrichedEtfService,
                basketCatalogService,
                basketPriceResolver,
                basketPortfolioValueCalculator,
                overlapCalculator,
                substituteApplier,
                quantityCalculator
        );

        List<BasketItem> composition = new ArrayList<>();
        composition.add(BasketItem.builder()
                .stockSymbol("AAPL")
                .etfWeight(50.0)
                .status(ItemStatus.MISSING)
                .build());
        composition.add(BasketItem.builder()
                .stockSymbol("GOOGL")
                .etfWeight(50.0)
                .status(ItemStatus.MISSING)
                .build());

        opportunity = BasketOpportunity.builder()
                .composition(composition)
                .build();
    }

    @Test
    void testCalculateBasketQuantities_ValidInvestment() {
        when(marketDataService.getCurrentPrices(anyList())).thenReturn(prices);

        BasketOpportunity result = basketEngineService.calculateBasketQuantities(10000.0, opportunity, false, null);

        assertNotNull(result);
        List<BasketItem> items = result.getComposition();

        assertEquals(33.0, items.get(0).getBuyQuantity());
        assertEquals(150.0, items.get(0).getLastPrice());
        assertEquals(2.0, items.get(1).getBuyQuantity());
        assertEquals(2500.0, items.get(1).getLastPrice());
    }

    @Test
    void testCalculateBasketQuantities_ZeroInvestment() {
        BasketOpportunity result = basketEngineService.calculateBasketQuantities(0.0, opportunity, false, null);
        assertEquals(opportunity, result);
        verifyNoInteractions(marketDataService);
    }

    @Test
    void testCalculateBasketQuantities_EmptyComposition() {
        opportunity.setComposition(Collections.emptyList());
        BasketOpportunity result = basketEngineService.calculateBasketQuantities(10000.0, opportunity, false, null);
        assertEquals(opportunity, result);
        verifyNoInteractions(marketDataService);
    }

    @Test
    void testCalculateBasketQuantities_PriceNotFound() {
        prices.remove("AAPL");
        when(marketDataService.getCurrentPrices(anyList())).thenReturn(prices);

        BasketOpportunity result = basketEngineService.calculateBasketQuantities(10000.0, opportunity, false, null);

        List<BasketItem> items = result.getComposition();
        assertEquals(0.0, items.get(0).getBuyQuantity());
        assertEquals(2.0, items.get(1).getBuyQuantity());
    }

    @Test
    void testCalculateBasketQuantities_IncludeHeld_MissingGap() {
        opportunity.getComposition().get(0).setStatus(ItemStatus.HELD);
        opportunity.getComposition().get(0).setHeldQuantity(10.0);

        when(marketDataService.getCurrentPrices(anyList())).thenReturn(prices);

        BasketOpportunity result = basketEngineService.calculateBasketQuantities(10000.0, opportunity, true, null);

        List<BasketItem> items = result.getComposition();
        assertEquals(0.0, items.get(0).getBuyQuantity());
        assertEquals(10.0, items.get(0).getTargetQuantity());
        assertEquals(0.0, items.get(1).getBuyQuantity());
    }

    @Test
    void testFindOpportunities_ByIsinList() {
        List<EquityHoldings> userHoldings = new ArrayList<>();
        EquityHoldings holding = new EquityHoldings();
        holding.setIsin("US0378331005");
        holding.setSymbol("AAPL");
        holding.setQuantity(10.0);
        holding.setCurrentValue(1500.0);
        userHoldings.add(holding);

        EtfData etfData = new EtfData();
        etfData.setName("Tech ETF");
        List<EtfHolding> etfHoldings = new ArrayList<>();
        EtfHolding etfHolding = new EtfHolding();
        etfHolding.setSymbol("AAPL");
        etfHolding.setIsin("US0378331005");
        etfHolding.setWeight(100.0);
        etfHoldings.add(etfHolding);
        etfData.setHoldings(etfHoldings);

        when(enrichedEtfService.getEnrichedEtfsBatch(anyList())).thenReturn(Map.of("IE00B53SZB19", etfData));

        List<BasketOpportunity> result = basketEngineService.findOpportunities(userHoldings, "IE00B53SZB19,");

        assertNotNull(result);
        assertEquals(1, result.size());
        assertEquals("Tech ETF", result.get(0).getEtfName());
        assertEquals(100.0, result.get(0).getMatchScore());
        verify(enrichedEtfService, times(1)).getEnrichedEtfsBatch(anyList());
        verify(etfApiClient, never()).enrichHoldings(anyList());
    }

    @Test
    void testFindOpportunities_KeywordTokenUsesSearch() {
        List<EquityHoldings> userHoldings = new ArrayList<>();
        EquityHoldings holding = new EquityHoldings();
        holding.setIsin("US0378331005");
        holding.setSymbol("AAPL");
        holding.setQuantity(10.0);
        holding.setCurrentValue(1500.0);
        userHoldings.add(holding);

        EtfData etfData = new EtfData();
        etfData.setName("Nifty Bees");
        etfData.setSymbol("NIFTYBEES");
        EtfHolding etfHolding = new EtfHolding();
        etfHolding.setSymbol("AAPL");
        etfHolding.setIsin("US0378331005");
        etfHolding.setWeight(100.0);
        etfData.setHoldings(List.of(etfHolding));

        when(etfApiClient.searchEtfs("Nifty 50")).thenReturn(List.of("NIFTYBEES"));
        when(enrichedEtfService.getEnrichedEtfsBatch(anyList())).thenReturn(Map.of("NIFTYBEES", etfData));

        List<BasketOpportunity> result = basketEngineService.findOpportunities(userHoldings, "Nifty 50");

        assertEquals(1, result.size());
        assertEquals("Nifty Bees", result.get(0).getEtfName());
        verify(etfApiClient).searchEtfs("Nifty 50");
    }

    @Test
    void resolveBaseTargetQty_minimumOneForSmallAllocations() {
        assertEquals(1, BasketUtils.resolveBaseTargetQty(500.0, 5000.0, 5.0));
        assertEquals(33, BasketUtils.resolveBaseTargetQty(5000.0, 150.0, 50.0));
        assertEquals(0, BasketUtils.resolveBaseTargetQty(0.0, 150.0, 50.0));
    }

    @Test
    void testCalculateBasketQuantities_SmallWeightGetsMinimumTargetQty() {
        List<BasketItem> composition = new ArrayList<>();
        composition.add(BasketItem.builder()
                .stockSymbol("OTHER")
                .etfWeight(99.5)
                .status(ItemStatus.MISSING)
                .build());
        composition.add(BasketItem.builder()
                .stockSymbol("LTIM")
                .etfWeight(0.5)
                .status(ItemStatus.MISSING)
                .build());
        opportunity.setComposition(composition);
        prices.put("OTHER", 100.0);
        prices.put("LTIM", 5000.0);
        when(marketDataService.getCurrentPrices(anyList())).thenReturn(prices);

        BasketOpportunity result = basketEngineService.calculateBasketQuantities(100000.0, opportunity, false, null);
        BasketItem ltim = result.getComposition().stream()
                .filter(i -> "LTIM".equals(i.getStockSymbol()))
                .findFirst()
                .orElseThrow();
        assertEquals(1.0, ltim.getTargetQuantity());
    }

    @Test
    void testGetEtfData_FallbackWarn() {
        when(enrichedEtfService.getEnrichedEtf("INVALID")).thenReturn(null);

        EtfData result = basketEngineService.getEtfData("INVALID");

        assertNull(result);
    }
}
