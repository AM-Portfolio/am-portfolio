package com.am.common.amcommondata.service;

import com.am.common.amcommondata.document.portfolio.PortfolioDocument;
import com.am.common.amcommondata.model.enums.BrokerType;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

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
    void upstoxStaysSeparate() {
        PortfolioDocument upstox = new PortfolioDocument();
        upstox.setId("3");
        upstox.setBrokerType(BrokerType.UPSTOX);
        upstox.setName("Upstox");

        assertEquals("broker:UPSTOX", PortfolioServiceImpl.brokerIdentityKey(upstox));
    }
}
