package com.portfolio.analytics.intelligence;

import com.am.common.amcommondata.model.PortfolioModelV1;
import com.am.common.amcommondata.model.asset.equity.EquityModel;
import com.am.common.amcommondata.model.enums.BrokerType;
import com.am.common.amcommondata.model.enums.PortfolioKind;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AggregatePortfolioFactoryTest {

    private final AggregatePortfolioFactory factory = new AggregatePortfolioFactory();

    @Test
    void mergesSameSymbolAcrossBrokers() {
        PortfolioModelV1 zerodha = broker("z", BrokerType.ZERODHA, equity("RELIANCE", 10, 100.0));
        PortfolioModelV1 upstox = broker("u", BrokerType.UPSTOX, equity("RELIANCE", 5, 120.0));

        PortfolioModelV1 merged = factory.mergeBrokerBooks("user-1", List.of(zerodha, upstox));

        assertEquals("user-1", merged.getOwner());
        assertEquals(1, merged.getEquityModels().size());
        EquityModel line = merged.getEquityModels().get(0);
        assertEquals("RELIANCE", line.getSymbol());
        assertEquals(15.0, line.getQuantity(), 1e-9);
        // cost-weighted avg: (10*100 + 5*120) / 15 = 106.666...
        assertEquals(106.6666666667, line.getAvgBuyingPrice(), 1e-6);
    }

    @Test
    void excludesBaskets() {
        PortfolioModelV1 broker = broker("b", BrokerType.ZERODHA, equity("TCS", 2, 50.0));
        PortfolioModelV1 basket = PortfolioModelV1.builder()
                .id(UUID.randomUUID())
                .owner("user-1")
                .portfolioKind(PortfolioKind.BASKET)
                .equityModels(List.of(equity("TCS", 100, 50.0)))
                .build();

        PortfolioModelV1 merged = factory.mergeBrokerBooks("user-1", List.of(broker, basket));

        assertEquals(1, merged.getEquityModels().size());
        assertEquals(2.0, merged.getEquityModels().get(0).getQuantity(), 1e-9);
    }

    @Test
    void emptyBrokersYieldEmptyEquities() {
        PortfolioModelV1 merged = factory.mergeBrokerBooks("user-1", List.of());
        assertTrue(merged.getEquityModels().isEmpty());
        assertEquals("user-1", merged.getOwner());
    }

    private static PortfolioModelV1 broker(String name, BrokerType type, EquityModel... equities) {
        return PortfolioModelV1.builder()
                .id(UUID.randomUUID())
                .name(name)
                .owner("user-1")
                .brokerType(type)
                .portfolioKind(PortfolioKind.BROKER)
                .equityModels(List.of(equities))
                .build();
    }

    private static EquityModel equity(String symbol, double qty, double avg) {
        return EquityModel.builder()
                .symbol(symbol)
                .quantity(qty)
                .avgBuyingPrice(avg)
                .build();
    }
}
