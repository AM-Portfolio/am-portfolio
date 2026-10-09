package com.portfolio.service.resolver;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@ExtendWith(MockitoExtension.class)
class MongoTradingSymbolResolverTest {

    private final MongoTradingSymbolResolver resolver = new MongoTradingSymbolResolver(null);

    @Test
    void returnsNormalizedTickerWhenSymbolIsAlreadyTicker() {
        assertEquals("RELIANCE", resolver.resolveTradingSymbol("NSE:RELIANCE-EQ", "INE002A01018"));
    }

    @Test
    void looksLikeIsin_detectsIndianEquityPattern() {
        assertTrue(com.portfolio.model.resolver.TradingSymbolResolver.looksLikeIsin("INE002A01018"));
    }

    @Test
    void whenMongoUnavailable_fallsBackToIsin() {
        assertEquals("INE002A01018", resolver.resolveTradingSymbol("INE002A01018", null));
    }
}
