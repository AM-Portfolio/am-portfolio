package com.am.common.amcommondata.service;

import com.am.common.amcommondata.document.portfolio.PortfolioDocument;
import com.am.common.amcommondata.model.PortfolioModelV1;
import com.am.common.amcommondata.model.enums.BrokerType;
import com.am.common.amcommondata.model.enums.PortfolioKind;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class BrokerIdentityKeyTest {

    @Test
    void growAndGrowwShareIdentity() {
        PortfolioDocument grow = new PortfolioDocument();
        grow.setId("1");
        grow.setBrokerType(BrokerType.GROW);
        grow.setName("Groww");

        PortfolioDocument groww = new PortfolioDocument();
        groww.setId("2");
        groww.setBrokerType(BrokerType.GROWW);
        groww.setName("Groww");

        assertEquals("broker:GROWW", PortfolioServiceImpl.brokerIdentityKey(grow));
        assertEquals("broker:GROWW", PortfolioServiceImpl.brokerIdentityKey(groww));
        assertEquals(PortfolioServiceImpl.brokerIdentityKey(grow), PortfolioServiceImpl.brokerIdentityKey(groww));
    }

    @Test
    void growthNameDoesNotCollapseIntoGroww() {
        assertEquals("name:growth fund",
                PortfolioServiceImpl.brokerIdentityKey(null, "Growth Fund", "x"));
    }

    @Test
    void upstoxStaysSeparate() {
        PortfolioDocument upstox = new PortfolioDocument();
        upstox.setId("3");
        upstox.setBrokerType(BrokerType.UPSTOX);
        upstox.setName("Upstox");

        assertEquals("broker:UPSTOX", PortfolioServiceImpl.brokerIdentityKey(upstox));
    }

    @Test
    void collapseForDisplay_keepsOneGrowwAndUpstox() {
        PortfolioModelV1 grow = PortfolioModelV1.builder()
                .id(UUID.randomUUID())
                .name("Groww")
                .brokerType(BrokerType.GROW)
                .portfolioKind(PortfolioKind.BROKER)
                .totalValue(100.0)
                .build();
        PortfolioModelV1 groww = PortfolioModelV1.builder()
                .id(UUID.randomUUID())
                .name("Groww")
                .brokerType(BrokerType.GROWW)
                .portfolioKind(PortfolioKind.BROKER)
                .totalValue(183.51)
                .build();
        PortfolioModelV1 upstox = PortfolioModelV1.builder()
                .id(UUID.randomUUID())
                .name("Upstox")
                .brokerType(BrokerType.UPSTOX)
                .portfolioKind(PortfolioKind.BROKER)
                .totalValue(799.0)
                .build();

        List<PortfolioModelV1> collapsed =
                PortfolioServiceImpl.collapseBrokerModelsForDisplay(List.of(grow, groww, upstox));

        assertEquals(2, collapsed.size());
        assertEquals(183.51, collapsed.stream()
                .filter(p -> p.getBrokerType() == BrokerType.GROWW || p.getBrokerType() == BrokerType.GROW)
                .findFirst().orElseThrow().getTotalValue());
        assertFalse(collapsed.stream().noneMatch(p -> p.getBrokerType() == BrokerType.UPSTOX));
    }
}
