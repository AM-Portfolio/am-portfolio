package com.portfolio.basket.service;

import com.portfolio.basket.client.EtfApiClient;
import com.portfolio.basket.engine.overlap.BasketOverlapCalculator;
import com.portfolio.basket.engine.sizing.BasketQuantityCalculator;
import com.portfolio.basket.engine.substitutes.BasketSubstituteApplier;
import com.portfolio.basket.kernel.BasketPortfolioValueCalculator;
import com.portfolio.basket.kernel.BasketPriceResolver;
import com.portfolio.basket.model.SubstituteAssignment;
import com.portfolio.basket.model.BasketOpportunity;
import com.portfolio.basket.model.BasketOpportunity.BasketItem;
import com.portfolio.basket.model.BasketOpportunity.ItemStatus;
import com.portfolio.basket.model.EtfData;
import com.portfolio.basket.model.EtfHolding;
import com.portfolio.basket.util.BasketNaming;
import com.portfolio.basket.util.SectorNormalizer;
import com.portfolio.model.portfolio.EquityHoldings;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anySet;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
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
    @Mock
    private BasketPriceResolver basketPriceResolver;
    @Mock
    private BasketPortfolioValueCalculator basketPortfolioValueCalculator;

    private BasketOverlapCalculator overlapCalculator;
    private BasketSubstituteApplier substituteApplier;
    private BasketQuantityCalculator quantityCalculator;
    private BasketEngineService engine;

    @BeforeEach
    void setUpKernelMocks() {
        lenient().when(basketPriceResolver.fetchPricesWithHoldingsFallback(anySet(), anyList()))
                .thenReturn(Collections.emptyMap());
        lenient().when(basketPortfolioValueCalculator.calculate(anyList()))
                .thenReturn(BasketPortfolioValueCalculator.PortfolioValues.builder()
                        .totalPortfolioValue(0.0)
                        .remainingPortfolioValue(0.0)
                        .build());

        overlapCalculator = new BasketOverlapCalculator(basketPriceResolver);
        substituteApplier = new BasketSubstituteApplier(marketDataService, enrichedEtfService, overlapCalculator);
        quantityCalculator = new BasketQuantityCalculator(marketDataService);
        engine = new BasketEngineService(
                etfApiClient,
                enrichedEtfService,
                basketCatalogService,
                basketPriceResolver,
                basketPortfolioValueCalculator,
                overlapCalculator,
                substituteApplier,
                quantityCalculator
        );
    }

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
        assertEquals(3, subCount, "INFY with 30% weight should cover all 3 missing rows of 10% each");
        assertEquals(0, opp.getMissingCount());
        opp.getComposition().stream()
                .filter(i -> i.getStatus() == ItemStatus.SUBSTITUTE)
                .forEach(sub -> {
                    assertNotNull(sub.getUserHoldingSymbol(), "Substitute must name user holding");
                    assertNotNull(sub.getHeldQuantity(), "Substitute must expose held quantity for UI");
                    assertTrue(sub.getHeldQuantity() > 0, "Substitute held quantity must be positive");
                    assertNotNull(sub.getHeldAveragePrice(), "Substitute must expose avg price");
                });
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

        BasketOpportunity opp = engine.getPreview("ETF1", List.of(infy, wipro));

        BasketOpportunity updated = engine.applySubstitutesOnExisting(opp, List.of(infy, wipro), List.of(
                new SubstituteAssignment("INE001", "INEINFY", null),
                new SubstituteAssignment("INE002", "INEINFY", null)
        ));

        assertEquals(2, updated.getAppliedSubstituteCount());
        long subCount = updated.getComposition().stream()
                .filter(i -> i.getStatus() == ItemStatus.SUBSTITUTE && "INEINFY".equals(i.getUserHoldingIsin()))
                .count();
        assertEquals(2, subCount, "Should split INFY to cover both HCLTECH and TECHM");
    }

    @Test
    void sectorNormalizer_aliasesSoftwareProducts() {
        assertEquals("information technology",
                SectorNormalizer.normalize("Software Products"));
        assertEquals(SectorNormalizer.normalize("IT"),
                SectorNormalizer.normalize("Software Products"));
        assertEquals(SectorNormalizer.normalize("IT"),
                SectorNormalizer.normalize("IT Services"));
    }

    @Test
    void unknownSector_missingStillGetsFallbackAlternatives() {
        EtfData etf = new EtfData();
        etf.setName("Nifty IT");
        etf.setHoldings(List.of(
                holding("INE001", "HCLTECH", "Unknown", 10),
                holding("INEINFY", "INFY", "Information Technology", 20)
        ));
        when(enrichedEtfService.getEnrichedEtf(anyString())).thenReturn(etf);

        EquityHoldings infy = EquityHoldings.builder()
                .isin("INEINFY").symbol("INFY").sector("Information Technology")
                .quantity(100.0).weightInPortfolio(40.0).averageBuyingPrice(1.0).build();
        EquityHoldings hdfc = EquityHoldings.builder()
                .isin("INEHDFC").symbol("HDFCBANK").sector("Financial Services")
                .quantity(50.0).weightInPortfolio(20.0).averageBuyingPrice(1.0).build();

        BasketOpportunity opp = engine.getPreview("ETF1", List.of(infy, hdfc));
        BasketOpportunity.BasketItem missing = opp.getComposition().stream()
                .filter(i -> "INE001".equals(i.getIsin()))
                .findFirst()
                .orElseThrow();
        assertEquals(ItemStatus.MISSING, missing.getStatus());
        assertFalse(missing.getAlternatives() == null || missing.getAlternatives().isEmpty(),
                "Unknown ETF sector should still expose unused holdings as swap alternatives");
        assertTrue(missing.getAlternatives().stream().anyMatch(a -> "INEHDFC".equals(a.getIsin())));
        List<BasketOpportunity.Alternative> alts = missing.getAlternatives();
        boolean found = alts.stream().anyMatch(a -> a.getIsin().equals("INEINFY"));
        assertTrue(found, "Held ISINs with remaining weight CAN appear in alternatives for splitting");
    }

    @Test
    void softwareProductsSector_canAutoSubstituteItPeer() {
        EtfData etf = new EtfData();
        etf.setName("Nifty IT");
        etf.setHoldings(List.of(
                holding("INEOFSS", "OFSS", "Software Products", 5)
        ));
        when(enrichedEtfService.getEnrichedEtf(anyString())).thenReturn(etf);

        EquityHoldings wipro = EquityHoldings.builder()
                .isin("INEWIPRO").symbol("WIPRO").sector("Computers - Software & Consulting")
                .quantity(40.0).weightInPortfolio(15.0).averageBuyingPrice(1.0).build();

        BasketOpportunity opp = engine.getPreview("ETF1", List.of(wipro));
        assertEquals(1, opp.getComposition().stream()
                .filter(i -> i.getStatus() == ItemStatus.SUBSTITUTE).count());
    }

    @Test
    void applySubstitutes_emptyHoldingsThrowsConflict() {
        BasketOpportunity base = BasketOpportunity.builder()
                .etfIsin("ETF1")
                .sectorialBasket(true)
                .composition(List.of(
                        BasketOpportunity.BasketItem.builder()
                                .isin("INE001")
                                .stockSymbol("HCLTECH")
                                .sector("Information Technology")
                                .status(ItemStatus.MISSING)
                                .etfWeight(10.0)
                                .build()))
                .build();

        assertThrows(IllegalStateException.class, () -> engine.applySubstitutesOnExisting(
                base,
                List.of(),
                List.of(new SubstituteAssignment("INE001", "INEINFY", null))));
    }

    @Test
    void applySubstitutes_sectorialRejectsCrossSector() {
        BasketOpportunity base = BasketOpportunity.builder()
                .etfIsin("ETF1")
                .sectorialBasket(true)
                .composition(List.of(
                        BasketOpportunity.BasketItem.builder()
                                .isin("INE001")
                                .stockSymbol("HCLTECH")
                                .sector("Information Technology")
                                .status(ItemStatus.MISSING)
                                .etfWeight(10.0)
                                .build()))
                .build();

        EquityHoldings hdfc = EquityHoldings.builder()
                .isin("INEHDFC")
                .symbol("HDFCBANK")
                .sector("Financial Services")
                .quantity(50.0)
                .weightInPortfolio(20.0)
                .averageBuyingPrice(1.0)
                .build();

        assertThrows(IllegalStateException.class, () -> engine.applySubstitutesOnExisting(
                base,
                List.of(hdfc),
                List.of(new SubstituteAssignment("INE001", "INEHDFC", null))));
    }

    @Test
    void missingAlternatives_excludeFullyConsumedPeers() {
        EtfData etf = new EtfData();
        etf.setName("Nifty IT");
        etf.setHoldings(List.of(
                holding("INE001", "HCLTECH", "Information Technology", 10),
                holding("INE002", "TECHM", "Information Technology", 10),
                holding("INEMPH", "MPHASIS", "Information Technology", 10)
        ));
        when(enrichedEtfService.getEnrichedEtf(anyString())).thenReturn(etf);

        EquityHoldings wipro = EquityHoldings.builder()
                .isin("INEWIPRO").symbol("WIPRO").sector("Information Technology")
                .quantity(100.0).weightInPortfolio(20.0).averageBuyingPrice(500.0).build();

        BasketOpportunity opp = engine.getPreview("ETF1", List.of(wipro));
        BasketItem mphasis = opp.getComposition().stream()
                .filter(i -> "MPHASIS".equals(i.getStockSymbol())).findFirst().orElseThrow();
        assertEquals(ItemStatus.MISSING, mphasis.getStatus());
        boolean wiproListed = mphasis.getAlternatives() != null && mphasis.getAlternatives().stream()
                .anyMatch(a -> "WIPRO".equals(a.getSymbol()));
        assertFalse(wiproListed, "WIPRO fully consumed by auto-subs should not appear as alternative");
    }

    @Test
    void applySubstitutes_reclaimsAutoSubWhenPeerFullyAllocated() {
        EtfData etf = new EtfData();
        etf.setName("Nifty IT");
        etf.setHoldings(List.of(
                holding("INE001", "HCLTECH", "Information Technology", 10),
                holding("INE002", "TECHM", "Information Technology", 10),
                holding("INEMPH", "MPHASIS", "Information Technology", 10)
        ));
        when(enrichedEtfService.getEnrichedEtf(anyString())).thenReturn(etf);

        EquityHoldings wipro = EquityHoldings.builder()
                .isin("INEWIPRO").symbol("WIPRO").sector("Information Technology")
                .quantity(100.0).weightInPortfolio(20.0).averageBuyingPrice(500.0).build();

        BasketOpportunity opp = engine.getPreview("ETF1", List.of(wipro));

        BasketOpportunity updated = engine.applySubstitutesOnExisting(opp, List.of(wipro), List.of(
                new SubstituteAssignment("INEMPH", "INEWIPRO", null)
        ));

        assertTrue(updated.getAppliedSubstituteCount() >= 1);
        assertTrue(updated.getComposition().stream().anyMatch(i ->
                "MPHASIS".equals(i.getStockSymbol())
                        && i.getStatus() == ItemStatus.SUBSTITUTE
                        && "WIPRO".equals(i.getUserHoldingSymbol())));
    }

    @Test
    void applySubstitutes_resolvesBySymbolWhenIsinBlank() {
        EtfData etf = new EtfData();
        etf.setName("Nifty IT");
        etf.setHoldings(List.of(
                holding("INE001", "HCLTECH", "Information Technology", 10)
        ));
        when(enrichedEtfService.getEnrichedEtf(anyString())).thenReturn(etf);

        EquityHoldings wipro = EquityHoldings.builder()
                .isin("INEWIPRO").symbol("WIPRO").sector("Information Technology")
                .quantity(50.0).weightInPortfolio(15.0).averageBuyingPrice(500.0).build();

        BasketOpportunity opp = engine.getPreview("ETF1", List.of(wipro));
        BasketOpportunity updated = engine.applySubstitutesOnExisting(opp, List.of(wipro), List.of(
                new SubstituteAssignment("INE001", null, null, "WIPRO")
        ));

        assertEquals(1, updated.getAppliedSubstituteCount());
        assertTrue(updated.getComposition().stream().anyMatch(i ->
                "HCLTECH".equals(i.getStockSymbol())
                        && i.getStatus() == ItemStatus.SUBSTITUTE
                        && "WIPRO".equals(i.getUserHoldingSymbol())));
    }

    @Test
    void getPreview_exposesSectorialProfileForItEtf() {
        EtfData etf = new EtfData();
        etf.setName("Nifty IT");
        etf.setHoldings(List.of(
                holding("INE001", "HCLTECH", "Information Technology", 40),
                holding("INE002", "TECHM", "Information Technology", 40),
                holding("INE003", "LTIM", "Information Technology", 20)
        ));
        when(enrichedEtfService.getEnrichedEtf(anyString())).thenReturn(etf);

        BasketOpportunity opp = engine.getPreview("ETF1", List.of());
        assertTrue(Boolean.TRUE.equals(opp.getSectorialBasket()));
        assertNotNull(opp.getDominantSector());
        assertEquals(3, opp.getEtfConstituentIsins().size());
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
