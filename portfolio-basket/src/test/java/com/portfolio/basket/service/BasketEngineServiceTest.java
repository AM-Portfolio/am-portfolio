package com.portfolio.basket.service;

import com.portfolio.basket.client.EtfApiClient;
import com.portfolio.basket.model.BasketOpportunity;
import com.portfolio.basket.model.BasketOpportunity.BasketItem;
import com.portfolio.basket.model.BasketOpportunity.ItemStatus;
import com.portfolio.basket.model.EtfData;
import com.portfolio.basket.model.EtfHolding;
import com.portfolio.model.portfolio.EquityHoldings;
import com.portfolio.marketdata.service.MarketDataService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class BasketEngineServiceTest {

    @Mock
    private EtfApiClient etfApiClient;

    @Mock
    private MarketDataService marketDataService;

    @InjectMocks
    private BasketEngineService basketEngineService;

    private BasketOpportunity opportunity;
    private Map<String, Double> prices;

    @BeforeEach
    void setUp() {
        prices = new HashMap<>();
        prices.put("AAPL", 150.0);
        prices.put("GOOGL", 2500.0);

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

        BasketOpportunity result = basketEngineService.calculateBasketQuantities(10000.0, opportunity, false);

        assertNotNull(result);
        List<BasketItem> items = result.getComposition();
        
        // AAPL: 50% of 10000 = 5000. 5000 / 150 = 33.33 -> 33
        assertEquals(33.0, items.get(0).getBuyQuantity());
        assertEquals(150.0, items.get(0).getLastPrice());

        // GOOGL: 50% of 10000 = 5000. 5000 / 2500 = 2
        assertEquals(2.0, items.get(1).getBuyQuantity());
        assertEquals(2500.0, items.get(1).getLastPrice());
    }

    @Test
    void testCalculateBasketQuantities_ZeroInvestment() {
        BasketOpportunity result = basketEngineService.calculateBasketQuantities(0.0, opportunity, false);
        assertEquals(opportunity, result);
        verifyNoInteractions(marketDataService);
    }

    @Test
    void testCalculateBasketQuantities_EmptyComposition() {
        opportunity.setComposition(Collections.emptyList());
        BasketOpportunity result = basketEngineService.calculateBasketQuantities(10000.0, opportunity, false);
        assertEquals(opportunity, result);
        verifyNoInteractions(marketDataService);
    }

    @Test
    void testCalculateBasketQuantities_PriceNotFound() {
        prices.remove("AAPL");
        when(marketDataService.getCurrentPrices(anyList())).thenReturn(prices);

        BasketOpportunity result = basketEngineService.calculateBasketQuantities(10000.0, opportunity, false);

        List<BasketItem> items = result.getComposition();
        // AAPL skipped because price not found
        assertNull(items.get(0).getBuyQuantity());
        // GOOGL should be calculated
        assertEquals(2.0, items.get(1).getBuyQuantity());
    }

    @Test
    void testCalculateBasketQuantities_IncludeHeld_MissingGap() {
        // Mark AAPL as HELD with quantity 10 (value 1500)
        opportunity.getComposition().get(0).setStatus(ItemStatus.HELD);
        opportunity.getComposition().get(0).setHeldQuantity(10.0);
        
        when(marketDataService.getCurrentPrices(anyList())).thenReturn(prices);

        // Total investment 10000. AAPL target 5000. 
        // Held value = 10 * 150 = 1500.
        // Required = 5000 - 1500 = 3500.
        // Qty to buy = 3500 / 150 = 23.33 -> 23.
        BasketOpportunity result = basketEngineService.calculateBasketQuantities(10000.0, opportunity, true);

        List<BasketItem> items = result.getComposition();
        assertEquals(23.0, items.get(0).getBuyQuantity());
        assertEquals(2.0, items.get(1).getBuyQuantity()); // GOOGL unaffected
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

        when(etfApiClient.fetchEtfHoldings("IE00B53SZB19")).thenReturn(etfData);

        List<BasketOpportunity> result = basketEngineService.findOpportunities(userHoldings, "IE00B53SZB19,");

        assertNotNull(result);
        assertEquals(1, result.size());
        assertEquals("Tech ETF", result.get(0).getEtfName());
        assertEquals(100.0, result.get(0).getMatchScore());
    }

    @Test
    void testGetEtfData_FallbackWarn() {
        when(etfApiClient.fetchEtfHoldings("INVALID")).thenReturn(null);
        
        EtfData result = basketEngineService.getEtfData("INVALID");
        
        assertNull(result);
    }
}
