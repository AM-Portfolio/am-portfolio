package com.am.common.amcommondata.mapper;

import com.am.common.amcommondata.document.basket.BasketPortfolioDocument;
import com.am.common.amcommondata.document.portfolio.PortfolioDocument;
import com.am.common.amcommondata.model.PortfolioModelV1;
import com.am.common.amcommondata.model.enums.PortfolioKind;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

class BasketPortfolioMapperTest {

    private static final LocalDateTime CREATED_AT = LocalDateTime.of(2026, 3, 15, 10, 30);
    private static final String SOURCE_ID = "11111111-1111-1111-1111-111111111111";

    @Test
    void dualRead_prefersNestedOverLegacyFlatFields() {
        PortfolioDocument document = PortfolioDocument.builder()
                .portfolioKind(PortfolioKind.BASKET)
                .sourcePortfolioId("legacy-source")
                .etfIsin("LEGACY")
                .etfName("Legacy ETF")
                .gapMissingCount(9)
                .investmentAmount(100.0)
                .replicaScore(50.0)
                .coverageAfterCreation(50.0)
                .createdFromBasketAt(CREATED_AT.minusDays(1))
                .basket(BasketPortfolioDocument.builder()
                        .sourcePortfolioId(SOURCE_ID)
                        .etfIsin("NESTED")
                        .etfName("Nifty IT")
                        .gapMissingCount(2)
                        .investmentAmount(50000.0)
                        .replicaScore(92.5)
                        .coverageAfterCreation(92.5)
                        .createdFromBasketAt(CREATED_AT)
                        .build())
                .build();

        PortfolioModelV1 model = PortfolioModelV1.builder().build();
        BasketPortfolioMapper.applyBasketFieldsToModel(model, document);

        assertEquals(SOURCE_ID, model.getSourcePortfolioId());
        assertEquals("NESTED", model.getEtfIsin());
        assertEquals("Nifty IT", model.getEtfName());
        assertEquals(2, model.getGapMissingCount());
        assertEquals(50000.0, model.getInvestmentAmount());
        assertEquals(92.5, model.getReplicaScore());
        assertEquals(92.5, model.getCoverageAfterCreation());
        assertEquals(CREATED_AT, model.getCreatedFromBasketAt());
    }

    @Test
    void dualRead_fallsBackToLegacyWhenNestedAbsent() {
        PortfolioDocument document = PortfolioDocument.builder()
                .portfolioKind(PortfolioKind.BASKET)
                .sourcePortfolioId(SOURCE_ID)
                .etfIsin("LEGACY")
                .etfName("Legacy ETF")
                .gapMissingCount(3)
                .investmentAmount(25000.0)
                .replicaScore(88.0)
                .coverageAfterCreation(88.0)
                .createdFromBasketAt(CREATED_AT)
                .build();

        PortfolioModelV1 model = PortfolioModelV1.builder().build();
        BasketPortfolioMapper.applyBasketFieldsToModel(model, document);

        assertEquals(SOURCE_ID, model.getSourcePortfolioId());
        assertEquals("LEGACY", model.getEtfIsin());
        assertEquals("Legacy ETF", model.getEtfName());
        assertEquals(3, model.getGapMissingCount());
        assertEquals(25000.0, model.getInvestmentAmount());
        assertEquals(88.0, model.getReplicaScore());
        assertEquals(CREATED_AT, model.getCreatedFromBasketAt());
    }

    @Test
    void dualWrite_populatesLegacyAndNestedForBasket() {
        PortfolioModelV1 model = sampleBasketModel();

        PortfolioDocument document = PortfolioDocument.builder()
                .portfolioKind(PortfolioKind.BASKET)
                .build();
        BasketPortfolioMapper.applyBasketFieldsToDocument(document, model);

        assertEquals(SOURCE_ID, document.getSourcePortfolioId());
        assertEquals("INE123", document.getEtfIsin());
        assertEquals("Nifty IT", document.getEtfName());
        assertEquals(2, document.getGapMissingCount());
        assertEquals(50000.0, document.getInvestmentAmount());
        assertEquals(92.5, document.getReplicaScore());
        assertEquals(92.5, document.getCoverageAfterCreation());
        assertEquals(CREATED_AT, document.getCreatedFromBasketAt());

        BasketPortfolioDocument nested = document.getBasket();
        assertNotNull(nested);
        assertEquals(SOURCE_ID, nested.getSourcePortfolioId());
        assertEquals("INE123", nested.getEtfIsin());
        assertEquals("Nifty IT", nested.getEtfName());
        assertEquals(2, nested.getGapMissingCount());
        assertEquals(50000.0, nested.getInvestmentAmount());
        assertEquals(92.5, nested.getReplicaScore());
        assertEquals(CREATED_AT, nested.getCreatedFromBasketAt());
    }

    @Test
    void dualWrite_skipsBasketFieldsForBrokerPortfolio() {
        PortfolioModelV1 model = sampleBasketModel();
        model.setPortfolioKind(PortfolioKind.BROKER);

        PortfolioDocument document = PortfolioDocument.builder()
                .portfolioKind(PortfolioKind.BROKER)
                .build();
        BasketPortfolioMapper.applyBasketFieldsToDocument(document, model);

        assertNull(document.getSourcePortfolioId());
        assertNull(document.getBasket());
    }

    @Test
    void roundtrip_nestedWriteThenReadPreservesBasketFields() {
        PortfolioModelV1 original = sampleBasketModel();

        PortfolioDocument document = PortfolioDocument.builder()
                .portfolioKind(PortfolioKind.BASKET)
                .build();
        BasketPortfolioMapper.applyBasketFieldsToDocument(document, original);

        PortfolioModelV1 roundTripped = PortfolioModelV1.builder().build();
        BasketPortfolioMapper.applyBasketFieldsToModel(roundTripped, document);

        assertEquals(original.getSourcePortfolioId(), roundTripped.getSourcePortfolioId());
        assertEquals(original.getEtfIsin(), roundTripped.getEtfIsin());
        assertEquals(original.getEtfName(), roundTripped.getEtfName());
        assertEquals(original.getGapMissingCount(), roundTripped.getGapMissingCount());
        assertEquals(original.getInvestmentAmount(), roundTripped.getInvestmentAmount());
        assertEquals(original.getReplicaScore(), roundTripped.getReplicaScore());
        assertEquals(original.getCoverageAfterCreation(), roundTripped.getCoverageAfterCreation());
        assertEquals(original.getCreatedFromBasketAt(), roundTripped.getCreatedFromBasketAt());
    }

    private static PortfolioModelV1 sampleBasketModel() {
        return PortfolioModelV1.builder()
                .id(UUID.randomUUID())
                .portfolioKind(PortfolioKind.BASKET)
                .sourcePortfolioId(SOURCE_ID)
                .etfIsin("INE123")
                .etfName("Nifty IT")
                .gapMissingCount(2)
                .investmentAmount(50000.0)
                .replicaScore(92.5)
                .coverageAfterCreation(92.5)
                .createdFromBasketAt(CREATED_AT)
                .build();
    }
}
