package com.portfolio.service.resolver;

import com.portfolio.marketdata.client.MarketDataApiClient;
import com.portfolio.model.resolver.TradingSymbolResolver;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MongoTradingSymbolResolverTest {

    @Mock
    private MarketDataApiClient marketDataApiClient;

    @Test
    void returnsNormalizedTickerWhenSymbolIsAlreadyTicker() {
        MongoTradingSymbolResolver resolver = new MongoTradingSymbolResolver(null);
        assertEquals("RELIANCE", resolver.resolveTradingSymbol("NSE:RELIANCE-EQ", "INE002A01018"));
    }

    @Test
    void looksLikeIsin_detectsIndianEquityPattern() {
        assertTrue(TradingSymbolResolver.looksLikeIsin("INE002A01018"));
    }

    @Test
    void looksLikeTradingTicker_rejectsCompanyNames() {
        assertFalse(TradingSymbolResolver.looksLikeTradingTicker("ADANI ENERGY SOLUTIONS"));
        assertTrue(TradingSymbolResolver.looksLikeTradingTicker("RELIANCE"));
        assertTrue(TradingSymbolResolver.looksLikeTradingTicker("NSE:RELIANCE-EQ"));
    }

    @Test
    void whenLookupUnavailable_companyNameFallsBackToIsin() {
        MongoTradingSymbolResolver resolver = new MongoTradingSymbolResolver(null);
        assertEquals("INE102V01018",
                resolver.resolveTradingSymbol("ADANI ENERGY SOLUTIONS", "INE102V01018"));
    }

    @Test
    void whenMongoUnavailable_fallsBackToIsin() {
        MongoTradingSymbolResolver resolver = new MongoTradingSymbolResolver(null);
        assertEquals("INE002A01018", resolver.resolveTradingSymbol("INE002A01018", null));
    }

    @Test
    void companyNameWithIsin_resolvesViaSecuritiesBatchSearch() {
        when(marketDataApiClient.resolveTickersByIsins(eq(List.of("INE102V01018"))))
                .thenReturn(Mono.just(Map.of("INE102V01018", "ADANIENSOL")));

        MongoTradingSymbolResolver resolver = new MongoTradingSymbolResolver(marketDataApiClient);
        assertEquals("ADANIENSOL",
                resolver.resolveTradingSymbol("ADANI ENERGY SOLUTIONS", "INE102V01018"));
    }
}
