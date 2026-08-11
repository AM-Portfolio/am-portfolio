package com.portfolio.basket.service;

import com.portfolio.basket.client.EtfApiClient;
import com.portfolio.basket.model.BasketOpportunity;
import com.portfolio.basket.model.BasketOpportunity.ItemStatus;
import com.portfolio.basket.model.EtfData;
import com.portfolio.basket.model.EtfHolding;
import com.portfolio.basket.util.BasketNaming;
import com.portfolio.basket.util.SectorNormalizer;
import com.portfolio.model.portfolio.EquityHoldings;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BasketEngineExclusiveSubstituteTest {

    @Mock
    private EtfApiClient etfApiClient;
    @Mock
    private EnrichedEtfService enrichedEtfService;
    @Mock
    private BasketCatalogService basketCatalogService;
    @Mock
    private com.portfolio.marketdata.service.MarketDataService marketDataService;

    @InjectMocks
    private BasketEngineService engine;

    @Test
    void sectorNormalizer_aliasesIt() {
        assertEquals(SectorNormalizer.normalize("IT"),
                SectorNormalizer.normalize("Computers - Software & Consulting"));
    }

    @Test
    void basketNaming_shortensEtf() {
        assertEquals("Nifty IT", BasketNaming.shorten("Nippon India ETF Nifty IT"));
        assertTrue(BasketNaming.defaultBasketName("Nippon India ETF Nifty IT", "Zerodha")
                .startsWith("Nifty IT · Zerodha"));
    }

    @Test
    void exclusiveSubstitute_samePeerNotUsedTwice() {
        EtfData etf = new EtfData();
        etf.setName("Nifty IT");
        etf.setHoldings(List.of(
                holding("INE001", "HCLTECH", "Information Technology", 10),
                holding("INE002", "TECHM", "Information Technology", 10),
                holding("INE003", "LTIM", "Information Technology", 10)
        ));
        when(enrichedEtfService.getEnrichedEtf(anyString())).thenReturn(etf);

        EquityHoldings infy = EquityHoldings.builder()
                .isin("INEINFY")
                .symbol("INFY")
                .sector("Information Technology")
                .quantity(100.0)
                .weightInPortfolio(30.0)
                .averageBuyingPrice(1500.0)
                .build();

        BasketOpportunity opp = engine.getPreview("ETF1", List.of(infy));
        long subCount = opp.getComposition().stream()
                .filter(i -> i.getStatus() == ItemStatus.SUBSTITUTE)
                .count();
        assertEquals(1, subCount, "One holding can cover only one missing row");
        assertEquals(2, opp.getMissingCount());
    }

    @Test
    void applySubstitutes_conflictOnDoubleAssign() {
        EtfData etf = new EtfData();
        etf.setName("Nifty IT");
        etf.setHoldings(List.of(
                holding("INE001", "HCLTECH", "Information Technology", 10),
                holding("INE002", "TECHM", "Information Technology", 10)
        ));
        when(enrichedEtfService.getEnrichedEtf(anyString())).thenReturn(etf);

        EquityHoldings infy = EquityHoldings.builder()
                .isin("INEINFY").symbol("INFY").sector("Information Technology")
                .quantity(50.0).weightInPortfolio(20.0).averageBuyingPrice(1.0).build();
        EquityHoldings wipro = EquityHoldings.builder()
                .isin("INEWIPRO").symbol("WIPRO").sector("Information Technology")
                .quantity(50.0).weightInPortfolio(10.0).averageBuyingPrice(1.0).build();

        assertThrows(IllegalStateException.class, () ->
                engine.applySubstitutes("ETF1", List.of(infy, wipro), List.of(
                        new BasketEngineService.SubstituteAssignment("INE001", "INEINFY"),
                        new BasketEngineService.SubstituteAssignment("INE002", "INEINFY")
                )));
    }

    private static EtfHolding holding(String isin, String symbol, String sector, double weight) {
        EtfHolding h = new EtfHolding();
        h.setIsin(isin);
        h.setSymbol(symbol);
        h.setSector(sector);
        h.setWeight(weight);
        return h;
    }
}
