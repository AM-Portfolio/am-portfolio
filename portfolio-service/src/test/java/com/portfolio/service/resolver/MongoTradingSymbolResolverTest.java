package com.portfolio.service.resolver;

import com.portfolio.marketdata.client.MarketDataApiClient;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MongoTradingSymbolResolverTest {

    @Mock
    private MarketDataApiClient marketDataApiClient;

    @Test
    void returnsNormalizedTickerWhenSymbolIsAlreadyTicker() {
        MongoTradingSymbolResolver resolver = new MongoTradingSymbolResolver(marketDataApiClient);
        assertEquals("RELIANCE", resolver.resolveTradingSymbol("NSE:RELIANCE-EQ", "INE002A01018"));
        verify(marketDataApiClient, never()).resolveTickerByIsin(anyString());
    }

    @Test
    void looksLikeIsin_detectsIndianEquityPattern() {
        assertTrue(com.portfolio.model.resolver.TradingSymbolResolver.looksLikeIsin("INE002A01018"));
    }

    @Test
    void whenClientUnavailable_fallsBackToIsin() {
        MongoTradingSymbolResolver resolver = new MongoTradingSymbolResolver(null);
        assertEquals("INE002A01018", resolver.resolveTradingSymbol("INE002A01018", null));
    }

    @Test
    void pointLookup_readsIsinKeyedMap_notNestedSymbolKey() {
        when(marketDataApiClient.resolveTickerByIsin("INE002A01018"))
                .thenReturn(Mono.just(Map.of("INE002A01018", "RELIANCE")));

        MongoTradingSymbolResolver resolver = new MongoTradingSymbolResolver(marketDataApiClient);
        assertEquals("RELIANCE", resolver.resolveTradingSymbol("INE002A01018", "INE002A01018"));
    }

    @Test
    void pointLookup_rejectsIsinShapedTickerResult() {
        when(marketDataApiClient.resolveTickerByIsin("INE002A01018"))
                .thenReturn(Mono.just(Map.of("INE002A01018", "INE002A01018")));

        MongoTradingSymbolResolver resolver = new MongoTradingSymbolResolver(marketDataApiClient);
        assertEquals("INE002A01018", resolver.resolveTradingSymbol("INE002A01018", null));
    }

    @Test
    void pointLookup_missingKey_fallsBackToIsin() {
        when(marketDataApiClient.resolveTickerByIsin("INE002A01018"))
                .thenReturn(Mono.just(Map.of()));

        MongoTradingSymbolResolver resolver = new MongoTradingSymbolResolver(marketDataApiClient);
        assertEquals("INE002A01018", resolver.resolveTradingSymbol("INE002A01018", null));
    }

    @Test
    void batchResolve_filtersIsinShapedValues() {
        when(marketDataApiClient.resolveTickersByIsins(anyList()))
                .thenReturn(Mono.just(Map.of(
                        "INE002A01018", "RELIANCE",
                        "INE669E01016", "INE669E01016")));

        MongoTradingSymbolResolver resolver = new MongoTradingSymbolResolver(marketDataApiClient);
        Map<String, String> result = resolver.resolveTradingSymbols(
                List.of("INE002A01018", "INE669E01016"));

        assertEquals(Map.of("INE002A01018", "RELIANCE"), result);
    }
}
