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

    @Test
    void preservesMutualFundAssetClassOnMerge() {
        EquityModel mf = EquityModel.builder()
                .symbol("INF090I01239")
                .quantity(100.0)
                .avgBuyingPrice(10.0)
                .currentValue(50_000.0)
                .assetClass("mutual_fund_demat")
                .build();
        PortfolioModelV1 cas = broker("cdsl", BrokerType.CDSL, mf);
        PortfolioModelV1 merged = factory.mergeBrokerBooks("user-1", List.of(cas));
        assertEquals(1, merged.getEquityModels().size());
        assertEquals(
                HealthScoreEngine.ASSET_MUTUAL_FUND,
                HealthScoreEngine.normalizeAssetClass(merged.getEquityModels().get(0).getAssetClass()));
    }

    @Test
    void liftsNpsBookEquitiesIntoNpsModels() {
        EquityModel scheme = EquityModel.builder()
                .symbol("SM001001")
                .quantity(10.0)
                .currentValue(20_000.0)
                .build();
        PortfolioModelV1 nps = PortfolioModelV1.builder()
                .id(UUID.randomUUID())
                .name("NPS")
                .owner("user-1")
                .brokerType(BrokerType.NPS)
                .portfolioKind(PortfolioKind.BROKER)
                .equityModels(List.of(scheme))
                .build();
        PortfolioModelV1 merged = factory.mergeBrokerBooks("user-1", List.of(nps));
        assertTrue(merged.getEquityModels() == null || merged.getEquityModels().isEmpty());
        assertEquals(1, merged.getNpsModels().size());
        assertEquals(HealthScoreEngine.ASSET_NPS, merged.getNpsModels().get(0).getAssetClass());
        assertEquals(20_000.0, merged.getNpsModels().get(0).getCurrentValue(), 1e-9);
    }

    @Test
    void skipsCdslWhenNamedBooksExist_avoidsDoubleCount() {
        PortfolioModelV1 groww = broker("Groww", BrokerType.GROWW, equity("IDEA", 10, 1.0));
        PortfolioModelV1 mirae = PortfolioModelV1.builder()
                .id(UUID.randomUUID())
                .name("Mirae")
                .owner("user-1")
                .brokerType(BrokerType.CAS_DEMAT)
                .dematBoid("12088700XXXX")
                .portfolioKind(PortfolioKind.BROKER)
                .equityModels(List.of(equity("IDEA", 10, 1.0)))
                .build();
        PortfolioModelV1 staleCdsl = broker("CDSL CAS", BrokerType.CDSL, equity("IDEA", 10, 1.0));

        PortfolioModelV1 merged = factory.mergeBrokerBooks("user-1", List.of(groww, mirae, staleCdsl));

        assertEquals(1, merged.getEquityModels().size());
        assertEquals("IDEA", merged.getEquityModels().get(0).getSymbol());
        assertEquals(20.0, merged.getEquityModels().get(0).getQuantity(), 1e-9);
    }

    @Test
    void brokerBookValuesSkipsZeroCurrent() {
        PortfolioModelV1 zero = PortfolioModelV1.builder()
                .id(UUID.randomUUID())
                .name("Zerodha")
                .owner("user-1")
                .brokerType(BrokerType.ZERODHA)
                .portfolioKind(PortfolioKind.BROKER)
                .totalValue(0.0)
                .equityModels(List.of())
                .build();
        PortfolioModelV1 live = PortfolioModelV1.builder()
                .id(UUID.randomUUID())
                .name("Dhan")
                .owner("user-1")
                .brokerType(BrokerType.DHAN)
                .portfolioKind(PortfolioKind.BROKER)
                .totalValue(100_000.0)
                .build();
        var values = AggregatePortfolioFactory.brokerBookValues(List.of(zero, live));
        assertEquals(1, values.size());
        assertEquals(100_000.0, values.get("Dhan"), 1e-9);
        assertTrue(!values.containsKey("Zerodha"));
    }

    @Test
    void brokerBookValuesUsesLineMarksWhenSavedTotalIsZero() {
        EquityModel line = equity("TITAN", 10, 100.0);
        line.setCurrentValue(930_000.0);
        PortfolioModelV1 zerodha = PortfolioModelV1.builder()
                .id(UUID.randomUUID())
                .name("Zerodha")
                .owner("user-1")
                .brokerType(BrokerType.ZERODHA)
                .portfolioKind(PortfolioKind.BROKER)
                .totalValue(0.0)
                .equityModels(List.of(line))
                .build();
        var values = AggregatePortfolioFactory.brokerBookValues(List.of(zerodha));
        assertEquals(930_000.0, values.get("Zerodha"), 1e-9);
    }

    @Test
    void brokerBookValuesSplitsDuplicateNamesByOwner() {
        PortfolioModelV1 yours = PortfolioModelV1.builder()
                .id(UUID.randomUUID())
                .name("Mirae")
                .owner("user-1")
                .brokerType(BrokerType.CAS_DEMAT)
                .portfolioKind(PortfolioKind.BROKER)
                .totalValue(1_000.0)
                .build();
        PortfolioModelV1 family = PortfolioModelV1.builder()
                .id(UUID.randomUUID())
                .name("Mirae")
                .owner("user-1")
                .familyMemberName("Test Holder")
                .brokerType(BrokerType.CAS_DEMAT)
                .portfolioKind(PortfolioKind.BROKER)
                .totalValue(2_000.0)
                .build();
        var values = AggregatePortfolioFactory.brokerBookValues(List.of(yours, family));
        assertEquals(1_000.0, values.get("Mirae (You)"), 1e-9);
        assertEquals(2_000.0, values.get("Mirae (Test Holder)"), 1e-9);
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
