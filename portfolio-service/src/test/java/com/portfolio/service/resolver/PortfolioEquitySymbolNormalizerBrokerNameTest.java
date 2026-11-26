package com.portfolio.service.resolver;

import com.am.common.amcommondata.model.asset.equity.EquityModel;
import com.portfolio.marketdata.client.MarketDataApiClient;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PortfolioEquitySymbolNormalizerBrokerNameTest {

    @Mock
    private MarketDataApiClient marketDataApiClient;

    @Test
    void replacesIsinWithBrokerEtfTickerFromName() {
        when(marketDataApiClient.resolveTickersByIsins(anyList())).thenReturn(Mono.just(Map.of()));
        when(marketDataApiClient.resolveTickersByQueries(anyList(), anyList())).thenReturn(Mono.just(Map.of()));

        PortfolioEquitySymbolNormalizer normalizer =
                new PortfolioEquitySymbolNormalizer(new MongoTradingSymbolResolver(marketDataApiClient), marketDataApiClient);

        EquityModel gold = new EquityModel();
        gold.setSymbol("INF277KA1976");
        gold.setIsin("INF277KA1976");
        gold.setName("TATAAML-TATAGOLD");

        normalizer.normalizeEquities(List.of(gold));

        assertEquals("TATAGOLD", gold.getSymbol());
        assertEquals("INF277KA1976", gold.getIsin());
    }

    @Test
    void forcesIsinBookKeyWhenPoisonedRecltdOnSchemeMf() {
        when(marketDataApiClient.resolveTickersByIsins(anyList()))
                .thenReturn(Mono.just(Map.of("INF194KB1AL4", "RECLTD")));
        when(marketDataApiClient.resolveTickersByQueries(anyList(), anyList())).thenReturn(Mono.just(Map.of()));

        PortfolioEquitySymbolNormalizer normalizer =
                new PortfolioEquitySymbolNormalizer(new MongoTradingSymbolResolver(marketDataApiClient), marketDataApiClient);

        EquityModel bandhan = new EquityModel();
        bandhan.setSymbol("RECLTD");
        bandhan.setIsin("INF194KB1AL4");
        bandhan.setAssetClass("mutual_fund_demat");
        bandhan.setName("BANDHAN AMC LTD#BANDHAN MF-BANDHAN SMALL CAP FUND-DIRECT-GROWTH");

        normalizer.normalizeEquities(List.of(bandhan));

        assertEquals("INF194KB1AL4", bandhan.getSymbol());
        assertEquals("INF194KB1AL4", bandhan.getIsin());
    }

    @Test
    void replacesUnlistedIsinWithCompressedNameNotJiofin() {
        when(marketDataApiClient.resolveTickersByIsins(anyList())).thenReturn(Mono.just(Map.of()));
        when(marketDataApiClient.resolveTickersByQueries(anyList(), anyList()))
                .thenReturn(Mono.just(Map.of("JIO PLATFORMS LIMITED", "JIOFIN")));

        PortfolioEquitySymbolNormalizer normalizer =
                new PortfolioEquitySymbolNormalizer(new MongoTradingSymbolResolver(marketDataApiClient), marketDataApiClient);

        EquityModel jio = new EquityModel();
        jio.setSymbol("INE0BJQ01010");
        jio.setIsin("INE0BJQ01010");
        jio.setName("JIO PLATFORMS LIMITED");

        normalizer.normalizeEquities(List.of(jio));

        assertEquals("JIOPLATFORMS", jio.getSymbol());
        assertEquals("INE0BJQ01010", jio.getIsin());
    }
}
