package com.am.common.amcommondata.model.enums;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PortfolioKindTest {

    @Test
    void isRealBrokerBook_requiresBrokerKindAndType() {
        assertTrue(PortfolioKind.isRealBrokerBook(PortfolioKind.BROKER, BrokerType.ZERODHA));
        assertTrue(PortfolioKind.isRealBrokerBook(null, BrokerType.GROWW));
        assertFalse(PortfolioKind.isRealBrokerBook(PortfolioKind.BROKER, null));
        assertFalse(PortfolioKind.isRealBrokerBook(PortfolioKind.PAPER, BrokerType.ZERODHA));
        assertFalse(PortfolioKind.isRealBrokerBook(PortfolioKind.DEMO, BrokerType.ZERODHA));
        assertFalse(PortfolioKind.isRealBrokerBook(PortfolioKind.BASKET, BrokerType.ZERODHA));
        assertFalse(PortfolioKind.isRealBrokerBook(PortfolioKind.DELETED, BrokerType.ZERODHA));
    }

    @Test
    void demoAndPaperHelpers() {
        assertTrue(PortfolioKind.isDemo(PortfolioKind.DEMO));
        assertTrue(PortfolioKind.isPaper(PortfolioKind.PAPER));
        assertFalse(PortfolioKind.isBroker(PortfolioKind.DEMO));
        assertFalse(PortfolioKind.isBroker(PortfolioKind.PAPER));
        assertTrue(PortfolioKind.isBroker(null));
    }
}
