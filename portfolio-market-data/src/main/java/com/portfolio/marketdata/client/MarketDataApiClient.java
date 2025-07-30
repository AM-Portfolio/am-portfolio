package com.portfolio.marketdata.client;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

import com.portfolio.marketdata.client.base.AbstractApiClient;
import com.portfolio.marketdata.config.MarketDataApiConfig;
import com.portfolio.marketdata.model.FilterType;
import com.portfolio.marketdata.model.HistoricalDataRequest;
import com.portfolio.marketdata.model.HistoricalDataResponseWrapper;
import com.portfolio.marketdata.model.InstrumentType;
import com.portfolio.marketdata.model.MarketDataResponseWrapper;
import com.portfolio.marketdata.model.TimeFrame;

import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Mono;

/**
 * Client for the Market Data API.
 */
@Slf4j
@Component
public class MarketDataApiClient extends AbstractApiClient {

    private final MarketDataApiConfig config;

    /**
     * Creates a new MarketDataApiClient with the specified configuration.
     * 
     * @param config the market data API configuration
     */
    public MarketDataApiClient(MarketDataApiConfig config) {
        super(createWebClient(config));
        this.config = config;
    }
    
    /**
     * Creates a WebClient with the specified configuration.
     * 
     * @param config the market data API configuration
     * @return a WebClient
     */
    private static WebClient createWebClient(MarketDataApiConfig config) {
        return WebClient.builder()
                .baseUrl(config.getBaseUrl())
                .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .defaultHeader(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE)
                .defaultHeader(HttpHeaders.USER_AGENT, "Mozilla/5.0")
                .build();
    }
    
    /**
     * Gets the WebClient instance. Protected for testing purposes.
     * 
     * @return the WebClient instance
     */
    protected WebClient getWebClient() {
        return webClient;
    }

    @Override
    public String getBaseUrl() {
        return config.getBaseUrl();
    }

    @Override
    public int getConnectionTimeout() {
        return config.getConnectionTimeout();
    }

    @Override
    public int getReadTimeout() {
        return config.getReadTimeout();
    }

    @Override
    public int getMaxRetryAttempts() {
        return config.getMaxRetryAttempts();
    }

    /**
     * Gets the OHLC data for the specified symbols.
     * 
     * @param symbols the symbols to get OHLC data for
     * @param refresh whether to refresh the data or use cached data (default: true)
     * @return a Mono of MarketDataResponseWrapper
     */
    public Mono<MarketDataResponseWrapper> getOhlcData(List<String> symbols, boolean refresh) {
        // URL encode each symbol individually to handle special characters like &
        String symbolsParam = symbols.stream()
                .map(symbol -> URLEncoder.encode(symbol, StandardCharsets.UTF_8))
                .collect(Collectors.joining(","));
                
        String path = config.getOhlcPath() + "?symbols=" + symbolsParam + "&refresh=" + refresh;
        log.debug("Fetching OHLC data for {} from {} with refresh={}", String.join(",", symbols), path, refresh);
        
        // Deserialize to MarketDataResponseWrapper
        return get(path, MarketDataResponseWrapper.class)
                .doOnSuccess(data -> log.debug("Successfully fetched OHLC data for {} with {} entries", String.join(",", symbols), 
                        data.getData() != null ? data.getData().size() : 0))
                .doOnError(e -> log.error("Failed to fetch OHLC data for {}: {}", String.join(",", symbols), e.getMessage()));
    }
    
    /**
     * Gets the OHLC data for the specified symbols with default refresh=true.
     * 
     * @param symbols the symbols to get OHLC data for
     * @return a Mono of MarketDataResponseWrapper
     */
    public Mono<MarketDataResponseWrapper> getOhlcData(List<String> symbols) {
        return getOhlcData(symbols, false);
    }

    /**
     * Gets the OHLC data for the specified symbols synchronously.
     * 
     * @param symbols the symbols to get OHLC data for
     * @param refresh whether to refresh the data or use cached data (default: true)
     * @return the MarketDataResponseWrapper
     */
    public MarketDataResponseWrapper getOhlcDataSync(List<String> symbols, boolean refresh) {
        return getOhlcData(symbols, refresh).block();
    }
    
    /**
     * Gets the OHLC data for the specified symbols synchronously with default refresh=true.
     * 
     * @param symbols the symbols to get OHLC data for
     * @return the MarketDataResponseWrapper
     */
    public MarketDataResponseWrapper getOhlcDataSync(List<String> symbols) {
        return getOhlcDataSync(symbols, true);
    }
    
    /**
     * Gets the current prices for the specified symbols.
     * 
     * @param symbols the symbols to get current prices for
     * @return a map of symbol to current price
     */
    public Map<String, Double> getCurrentPrices(List<String> symbols) {
        MarketDataResponseWrapper wrapper = getOhlcDataSync(symbols);
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
        
        // URL encode each symbol individually to handle special characters
        String symbolsParam = request.getSymbols().stream()
                .map(symbol -> URLEncoder.encode(symbol, StandardCharsets.UTF_8))
                .collect(Collectors.joining(","));
        
        StringBuilder pathBuilder = new StringBuilder(config.getHistoricalDataPath())
                .append("?symbols=").append(symbolsParam)
                .append("&from=").append(request.getFromDate())
                .append("&to=").append(request.getToDate())
                .append("&interval=").append(request.getTimeFrame().getValue())
                .append("&instrumentType=").append(request.getInstrumentType().getValue())
                .append("&filterType=").append(request.getFilterType().getValue());
        
        // Add optional parameters if provided
        if (request.getFilterFrequency() != null && FilterType.CUSTOM.equals(request.getFilterType())) {
            pathBuilder.append("&filterFrequency=").append(request.getFilterFrequency());
        }
        
        if (request.getContinuous() != null) {
            pathBuilder.append("&continuous=").append(request.getContinuous());
        }
        
        if (request.getRefresh() != null) {
            pathBuilder.append("&refresh=").append(request.getRefresh());
        }
        
        String path = pathBuilder.toString();
        log.debug("Fetching historical data for {} from {} to {} with interval={}, filterType={}", 
                String.join(",", request.getSymbols()), request.getFromDate(), request.getToDate(), 
                request.getTimeFrame(), request.getFilterType());
        
        return get(path, HistoricalDataResponseWrapper.class)
                .doOnSuccess(data -> log.debug("Successfully fetched historical data for {} with {} data points", 
                        String.join(",", request.getSymbols()), data.getTotalDataPoints()))
                .doOnError(e -> log.error("Failed to fetch historical data for {}: {}", 
                        String.join(",", request.getSymbols()), e.getMessage()));
    }
    
    /**
     * Gets historical market data for the specified symbols with various filtering options.
     * Simplified version with fewer parameters and defaults for filterFrequency and continuous.
     * 
     * @param symbols the symbols to get historical data for
     * @param fromDate the start date for historical data (inclusive)
     * @param toDate the end date for historical data (inclusive)
     * @param timeFrame the time interval for data points (e.g., DAY, FIFTEEN_MIN)
     * @param instrumentType the instrument type (e.g., EQ for equity)
     * @param filterType the type of filtering to apply (ALL, START_END, CUSTOM)
     * @return a Mono of HistoricalDataResponseWrapper
     */
    public Mono<HistoricalDataResponseWrapper> getHistoricalData(
            List<String> symbols, 
            LocalDate fromDate, 
            LocalDate toDate, 
            TimeFrame timeFrame, 
            InstrumentType instrumentType, 
            FilterType filterType) {
    
        return getHistoricalData(HistoricalDataRequest.builder()
                .symbols(symbols)
                .fromDate(fromDate)
                .toDate(toDate)
                .timeFrame(timeFrame)
                .instrumentType(instrumentType)
                .filterType(filterType)
                .build());
    }
    
    /**
     * Gets historical market data for the specified symbols synchronously.
     * 
     * @param request the historical data request parameters
     * @return the HistoricalDataResponseWrapper
     */
    public HistoricalDataResponseWrapper getHistoricalDataSync(HistoricalDataRequest request) {
        return getHistoricalData(request).block();
    }

    /**
     * Gets historical market data for the specified symbols synchronously.
     * 
     * @param symbols the symbols to get historical data for
     * @param fromDate the start date for historical data (inclusive)
     * @param toDate the end date for historical data (inclusive)
     * @param timeFrame the time interval for data points (e.g., DAY, FIFTEEN_MIN)
     * @param instrumentType the instrument type (e.g., EQ for equity)
     * @param filterType the type of filtering to apply (ALL, START_END, CUSTOM)
     * @param filterFrequency the frequency for CUSTOM filtering (required when filterType is CUSTOM)
     * @param continuous whether to use continuous data (optional)
     * @return the HistoricalDataResponseWrapper
     */
    public HistoricalDataResponseWrapper getHistoricalDataSync(
            List<String> symbols, 
            LocalDate fromDate, 
            LocalDate toDate, 
            TimeFrame timeFrame, 
            InstrumentType instrumentType, 
            FilterType filterType, 
            Integer filterFrequency,
            Boolean continuous) {
    
        return getHistoricalDataSync(HistoricalDataRequest.builder()
                .symbols(symbols)
                .fromDate(fromDate)
                .toDate(toDate)
                .timeFrame(timeFrame)
                .instrumentType(instrumentType)
                .filterType(filterType)
                .filterFrequency(filterFrequency)
                .continuous(continuous)
                .build());
    }
    
    /**
     * Gets historical market data for the specified symbols synchronously.
     * Simplified version with fewer parameters and defaults for filterFrequency and continuous.
     * 
     * @param symbols the symbols to get historical data for
     * @param fromDate the start date for historical data (inclusive)
     * @param toDate the end date for historical data (inclusive)
     * @param timeFrame the time interval for data points (e.g., DAY, FIFTEEN_MIN)
     * @param instrumentType the instrument type (e.g., EQ for equity)
     * @param filterType the type of filtering to apply (ALL, START_END, CUSTOM)
     * @return the HistoricalDataResponseWrapper
     */
    public HistoricalDataResponseWrapper getHistoricalDataSync(
            List<String> symbols, 
            LocalDate fromDate, 
            LocalDate toDate, 
            TimeFrame timeFrame, 
            InstrumentType instrumentType, 
            FilterType filterType) {
    
        return getHistoricalDataSync(HistoricalDataRequest.builder()
                .symbols(symbols)
                .fromDate(fromDate)
                .toDate(toDate)
                .timeFrame(timeFrame)
                .instrumentType(instrumentType)
                .filterType(filterType)
                .build());
    }
}
