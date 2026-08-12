package com.portfolio.marketdata.client;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Date;

import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.portfolio.marketdata.client.base.AbstractApiClient;
import com.portfolio.marketdata.config.MarketDataApiConfig;
import com.portfolio.marketdata.model.HistoricalDataRequest;
import com.portfolio.marketdata.model.HistoricalDataResponseWrapper;
import com.portfolio.marketdata.model.MarketDataResponse;
import com.portfolio.marketdata.model.MarketDataResponseWrapper;
import com.portfolio.marketdata.model.OhlcDataRequest;
import com.portfolio.model.market.TimeFrame;

import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Mono;

import org.springframework.web.reactive.function.client.WebClient;

/**
 * Client for the Market Data API.
 */
@Slf4j
@Component
public class MarketDataApiClient extends AbstractApiClient {

        /**
         * Creates a new MarketDataApiClient with the specified configuration.
         * 
         * @param webClientBuilder the WebClient.Builder (auto-configured by Spring Boot)
         * @param config the market data API configuration
         */
        public MarketDataApiClient(WebClient.Builder webClientBuilder, MarketDataApiConfig config) {
                super(webClientBuilder, config);
        }

        /**
         * Gets the OHLC data for the specified symbols with a specific time frame.
         * 
         * @param symbols   the symbols to get OHLC data for
         * @param timeFrame the time frame for the OHLC data
         * @param refresh   whether to refresh the data or use cached data
         * @return a Mono of MarketDataResponseWrapper
         */
        @io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker(name = "ohlcDataApi")
        public Mono<MarketDataResponseWrapper> getOhlcData(List<String> symbols, String timeFrame, boolean refresh) {
                String symbolsParam = String.join(",", symbols);

                OhlcDataRequest request = OhlcDataRequest.builder()
                                .symbols(symbolsParam)
                                .timeFrame(timeFrame)
                                .refresh(refresh)
                                .indexSymbol(false)
                                .build();

                log.debug("Fetching OHLC data for {} with timeFrame={} from {} with refresh={}",
                                String.join(",", symbols), timeFrame, config.getOhlcEndpoint(), refresh);

                // Use POST with the request body, expecting a raw Map
                return post(config.getOhlcEndpoint(), request, Map.class)
                                .map(rawMap -> {
                                        ObjectMapper mapper = new ObjectMapper();
                                        mapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

                                        MarketDataResponseWrapper wrapper = new MarketDataResponseWrapper();
                                        wrapper.setCached(!refresh);
                                        wrapper.setTimestamp(new Date().getTime());

                                        Map<String, MarketDataResponse> dataMap = new HashMap<>();
                                        if (rawMap != null) {
                                                // Unwrap {"data": {...}} if the API uses a wrapper
                                                Object actualData = rawMap.containsKey("data") ? rawMap.get("data") : rawMap;
                                                if (actualData instanceof Map) {
                                                        Map<?, ?> dataToProcess = (Map<?, ?>) actualData;
                                                        for (Object key : dataToProcess.keySet()) {
                                                                try {
                                                                        Object value = dataToProcess.get(key);
                                                                        MarketDataResponse response = mapper.convertValue(value,
                                                                                        MarketDataResponse.class);
                                                                        dataMap.put(String.valueOf(key), response);
                                                                } catch (Exception e) {
                                                                        log.error("Error converting response for symbol {}",
                                                                                        key, e);
                                                                }
                                                        }
                                                } else {
                                                        log.error("Expected payload to be a Map but got {}", actualData != null ? actualData.getClass().getName() : "null");
                                                        throw new IllegalStateException("Invalid payload structure from Market Data API: expected Map");
                                                }
                                        }
                                        wrapper.setData(dataMap);
                                        return wrapper;
                                })
                                .doOnSuccess(data -> log.debug("Successfully fetched OHLC data for {} with {} entries",
                                                String.join(",", symbols),
                                                data.getData() != null ? data.getData().size() : 0))
                                .doOnError(e -> log.error("Failed to fetch OHLC data for {}: {}",
                                                String.join(",", symbols), e.getMessage()));
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
                                                entry -> entry.getValue().getLastPrice()));
        }

        /**
         * Gets historical market data for the specified symbols with various filtering
         * options.
         * 
         * @param request the historical data request parameters
         * @return a Mono of HistoricalDataResponseWrapper
         */
        @io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker(name = "historicalDataApi")
        public Mono<HistoricalDataResponseWrapper> getHistoricalData(HistoricalDataRequest request) {
                // Ensure we have a comma-separated string of symbols

                log.debug("Fetching historical data for {} from {} to {} with interval={}, filterType={}, forceRefresh={}",
                                request.getSymbols(), request.getFromDate(), request.getToDate(),
                                request.getInterval(), request.getFilterType(), request.getForceRefresh());

                // Use POST with the request body
                return post(config.getHistoricalDataEndpoint(), request, HistoricalDataResponseWrapper.class)
                                .doOnSuccess(data -> log.debug(
                                                "Successfully fetched historical data for {} with {} data points",
                                                request.getSymbols(), data.getTotalDataPoints()))
                                .doOnError(e -> log.error("Failed to fetch historical data for {}: {}",
                                                request.getSymbols(), e.getMessage()));
        }

        /**
         * Batch search securities
         */
        public Mono<com.portfolio.marketdata.model.BatchSearchResponse> batchSearch(
                        com.portfolio.marketdata.model.BatchSearchRequest request) {
                String path = config.getSecuritiesEndpoint() + "/batch-search";
                log.debug("Batch searching securities with {} queries", request.getQueries().size());

                return post(path, request, com.portfolio.marketdata.model.BatchSearchResponse.class)
                                .doOnSuccess(data -> log.debug("Successfully batch searched securities. Matches: {}",
                                                data.getTotalMatches()))
                                .doOnError(e -> log.error("Failed to batch search: {}", e.getMessage()));
        }

        /**
         * Gets historical charts data from am-market.
         * 
         * @param symbols the symbols to fetch charts for
         * @param range the timeframe range (e.g. 1D, 1M, 1Y)
         * @return a Mono of HistoricalChartsResponse
         */
        public Mono<com.portfolio.marketdata.model.HistoricalChartsResponse> getHistoricalCharts(List<String> symbols, String range) {
                String symbolsParam = String.join(",", symbols);
                String traceId = org.slf4j.MDC.get("traceId");
                log.info("Fetching historical charts for symbols={} range={} from {} traceId={}",
                                symbolsParam, range, config.getHistoricalChartsEndpoint(), traceId);

                return get(config.getHistoricalChartsEndpoint(),
                                com.portfolio.marketdata.model.HistoricalChartsResponse.class,
                                "symbols", symbolsParam,
                                "range", range,
                                "isIndexSymbol", false)
                                .doOnSuccess(data -> log.debug("Successfully fetched historical charts, traceId={}", traceId))
                                .doOnError(e -> log.error("Failed to fetch historical charts: {}, traceId={}", e.getMessage(), traceId));
        }

        /**
         * Resolves the trading symbol from Market Data service dynamically for a given ISIN code.
         * Used to map uploaded ISIN codes (like INF666M01IO8) to NSE/BSE tickers (like GROWWDEFNC)
         * without hardcoding and respecting microservice database isolation.
         *
         * @param isin the ISIN code to resolve
         * @return a Mono containing a map of isin and resolved symbol
         */
        @SuppressWarnings("rawtypes")
        public Mono<Map> resolveTickerByIsin(String isin) {
                String path = "/v1/market-data/instruments/isin/" + isin.trim().toUpperCase();
                log.info("Resolving ticker symbol by ISIN via API: {}", path);
                return get(path, Map.class)
                                .doOnSuccess(data -> log.debug("Successfully resolved ISIN {} to symbol {}", 
                                                isin, data != null ? data.get("symbol") : "null"))
                                .doOnError(e -> log.error("Failed to resolve ticker symbol for ISIN {}: {}", 
                                                isin, e.getMessage()));
        }
}
