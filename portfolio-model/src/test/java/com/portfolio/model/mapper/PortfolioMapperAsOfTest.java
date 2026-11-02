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
import com.portfolio.model.events.PortfolioUpdateEvent;

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
    void withoutAsOfLeavesCreatedAtNull() {
        PortfolioUpdateEvent event = PortfolioUpdateEvent.builder()
                .userId("user-1")
                .brokerType(BrokerType.ZERODHA)
                .portfolioId("doc-1")
                .equities(List.of(EquityModel.builder()
                        .symbol("TCS")
                        .quantity(1.0)
                        .avgBuyingPrice(1.0)
                        .build()))
                .build();

        PortfolioModelV1 model = mapper.toPortfolioModelV1(event);
        assertNull(model.getEquityModels().get(0).getCreatedAt());
    }
}
