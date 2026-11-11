package com.portfolio.model.mapper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;

import com.am.common.amcommondata.model.PortfolioModelV1;
import com.am.common.amcommondata.model.asset.equity.EquityModel;
import com.am.common.amcommondata.model.enums.BrokerType;
import com.am.common.amcommondata.model.enums.PortfolioKind;
import com.portfolio.model.events.DematRef;
import com.portfolio.model.events.PortfolioUpdateEvent;
import com.portfolio.model.events.trade.TradePortfolioSyncEvent;

class PortfolioMapperAsOfTest {

    private final PortfolioMapperv1 mapper = new PortfolioMapperv1(Optional.empty());

    @Test
    void stampsEquityCreatedAtFromAsOfDate() {
        LocalDate asOf = LocalDate.of(2024, 5, 15);
        PortfolioUpdateEvent event = PortfolioUpdateEvent.builder()
                .userId("user-1")
                .brokerType(BrokerType.ZERODHA)
                .portfolioId("doc-1")
                .asOfDate(asOf)
                .equities(List.of(EquityModel.builder()
                        .symbol("RELIANCE")
                        .quantity(10.0)
                        .avgBuyingPrice(100.0)
                        .build()))
                .build();

        PortfolioModelV1 model = mapper.toPortfolioModelV1(event);
        assertNotNull(model.getEquityModels());
        assertEquals(asOf.atStartOfDay(), model.getEquityModels().get(0).getCreatedAt());
        assertEquals(asOf.atStartOfDay(), model.getCreatedAt());
    }

    @Test
    void casDemat_mapsShortNameAndDematBoid() {
        PortfolioUpdateEvent event = PortfolioUpdateEvent.builder()
                .userId("user-1")
                .brokerType(BrokerType.CAS_DEMAT)
                .name("Mirae")
                .portfolioId("Mirae")
                .dematRef(DematRef.builder().boid("1208870012345678").dpName("MIRAE ASSET").build())
                .asOfDate(LocalDate.of(2024, 5, 15))
                .equities(List.of(EquityModel.builder()
                        .symbol("IDEA")
                        .quantity(1.0)
                        .build()))
                .build();

        PortfolioModelV1 model = mapper.toPortfolioModelV1(event);
        assertEquals("Mirae", model.getName());
        assertEquals(BrokerType.CAS_DEMAT, model.getBrokerType());
        assertEquals("1208870012345678", model.getDematBoid());
    }

    @Test
    void opaqueMongoDocIdIsNotUsedAsPortfolioName() {
        PortfolioUpdateEvent event = PortfolioUpdateEvent.builder()
                .userId("user-1")
                .brokerType(BrokerType.DHAN)
                .portfolioId("6ae8f136a20959e873d75e08")
                .asOfDate(LocalDate.of(2024, 5, 15))
                .equities(List.of(EquityModel.builder()
                        .symbol("RELIANCE")
                        .quantity(1.0)
                        .avgBuyingPrice(1.0)
                        .build()))
                .build();

        PortfolioModelV1 model = mapper.toPortfolioModelV1(event);
        assertEquals("Dhan", model.getName());
    }

    @Test
    void withoutAsOfLeavesCreatedAtNullEvenWhenTimestampPresent() {
        PortfolioUpdateEvent event = PortfolioUpdateEvent.builder()
                .userId("user-1")
                .brokerType(BrokerType.ZERODHA)
                .portfolioId("doc-1")
                .timestamp(java.time.LocalDateTime.now())
                .equities(List.of(EquityModel.builder()
                        .symbol("TCS")
                        .quantity(1.0)
                        .avgBuyingPrice(1.0)
                        .build()))
                .build();

        PortfolioModelV1 model = mapper.toPortfolioModelV1(event);
        // Ingest timestamp must not become lifeStart — catch-up uses ~90d when null.
        assertNull(model.getEquityModels().get(0).getCreatedAt());
        assertNull(model.getCreatedAt());
    }

    @Test
    void tradeSync_preservesPaperKind() {
        TradePortfolioSyncEvent trade = new TradePortfolioSyncEvent();
        trade.setId(java.util.UUID.randomUUID().toString());
        trade.setPortfolioId("Paper");
        trade.setUserId("user-1");
        trade.setBrokerType("ZERODHA");
        trade.setPortfolioKind("PAPER");
        trade.setAction("UPSERT");

        PortfolioModelV1 model = mapper.toPortfolioModelV1(trade);
        assertEquals(PortfolioKind.PAPER, model.getPortfolioKind());
    }

    @Test
    void tradeSync_paperNameHeuristic_whenKindBlank() {
        TradePortfolioSyncEvent trade = new TradePortfolioSyncEvent();
        trade.setId(java.util.UUID.randomUUID().toString());
        trade.setPortfolioId("paper");
        trade.setUserId("user-1");
        trade.setBrokerType("ZERODHA");
        trade.setAction("UPSERT");

        assertEquals(PortfolioKind.PAPER, PortfolioMapperv1.resolveTradePortfolioKind(trade));
    }
}
