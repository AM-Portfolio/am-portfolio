package com.portfolio.marketdata.client;

import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Component;

import com.portfolio.marketdata.client.base.AbstractApiClient;
import com.portfolio.marketdata.config.MarketDataApiConfig;
import com.portfolio.marketdata.model.HistoricalDataRequest;
import com.portfolio.marketdata.model.HistoricalDataResponseWrapper;
import com.portfolio.marketdata.model.MarketDataResponseWrapper;
import com.portfolio.marketdata.model.OhlcDataRequest;
import com.portfolio.model.market.TimeFrame;

import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Mono;

/**
 * Client for the Market Data API.
 */
@Slf4j
@Component
public class MarketDataApiClient extends AbstractApiClient {

    /**
     * Creates a new MarketDataApiClient with the specified configuration.
     * 
     * @param config the market data API configuration
     */
    public MarketDataApiClient(MarketDataApiConfig config) {
        super(config);
    }
    
    /**
     * Gets the OHLC data for the specified symbols with a specific time frame.
     * 
     * @param symbols the symbols to get OHLC data for
     * @param timeFrame the time frame for the OHLC data
     * @param refresh whether to refresh the data or use cached data
     * @return a Mono of MarketDataResponseWrapper
     */
    public Mono<MarketDataResponseWrapper> getOhlcData(List<String> symbols, String timeFrame, boolean refresh) {
        String symbolsParam = String.join(",", symbols);
        
        OhlcDataRequest request = OhlcDataRequest.builder()
                .symbols(symbolsParam)
                .timeFrame(timeFrame)
                .forceRefresh(refresh)
                .indexSymbol(false)
                .build();
        
        log.debug("Fetching OHLC data for {} with timeFrame={} from {} with refresh={}", 
                String.join(",", symbols), timeFrame, config.getOhlcEndpoint(), refresh);
        
        // Use POST with the request body
        return post(config.getOhlcEndpoint(), request, MarketDataResponseWrapper.class)
                .doOnSuccess(data -> log.debug("Successfully fetched OHLC data for {} with {} entries", String.join(",", symbols), 
                        data.getData() != null ? data.getData().size() : 0))
                .doOnError(e -> log.error("Failed to fetch OHLC data for {}: {}", String.join(",", symbols), e.getMessage()));
    }
    
    
    /**
     * Gets the current prices for the specified symbols.
     * 
     * @param symbols the symbols to get current prices for
     * @return a map of symbol to current price
     */
    public Map<String, Double> getCurrentPrices(List<String> symbols) {
        MarketDataResponseWrapper wrapper = getOhlcData(symbols, TimeFrame.FIVE_MIN.getValue(), false).block();
        if (wrapper.getData() == null) {
            return Map.of();
        }
        return wrapper.getData().entrySet().stream()
                .collect(java.util.stream.Collectors.toMap(
                        Map.Entry::getKey,
                        entry -> entry.getValue().getLastPrice()
                ));
    }
    
    /**
     * Gets historical market data for the specified symbols with various filtering options.
     * 
     * @param request the historical data request parameters
     * @return a Mono of HistoricalDataResponseWrapper
     */
    public Mono<HistoricalDataResponseWrapper> getHistoricalData(HistoricalDataRequest request) {
        // Ensure we have a comma-separated string of symbols
        if (request.getSymbols() == null && request.getSymbolsList() != null) {
            request.setSymbols(String.join(",", request.getSymbolsList()));
        }
        
        log.debug("Fetching historical data for {} from {} to {} with interval={}, filterType={}, forceRefresh={}", 
                request.getSymbols(), request.getFromDate(), request.getToDate(), 
                request.getTimeFrame(), request.getFilterType(), request.getForceRefresh());
        
        // Use POST with the request body
        return post(config.getHistoricalDataEndpoint(), request, HistoricalDataResponseWrapper.class)
                .doOnSuccess(data -> log.debug("Successfully fetched historical data for {} with {} data points", 
                        request.getSymbols(), data.getTotalDataPoints()))
                .doOnError(e -> log.error("Failed to fetch historical data for {}: {}", 
                        request.getSymbols(), e.getMessage()));
    }
    
}
