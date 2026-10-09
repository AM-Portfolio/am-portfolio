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
                if (request.getSymbols() != null && !request.getSymbols().isEmpty()) {
                        String[] parts = request.getSymbols().split(",");
                        java.util.List<String> updated = new java.util.ArrayList<>();
                        for (String part : parts) {
                                String s = part.trim();
                                if (!s.contains(":") && !s.contains(" ")) {
                                        updated.add("NSE:" + s);
                                } else {
                                        updated.add(s);
                                }
                        }
                        request.setSymbols(String.join(",", updated));
                }

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
        @SuppressWarnings({ "rawtypes", "unchecked" })
        public Mono<Map> resolveTickerByIsin(String isin) {
                return resolveTickersByIsins(List.of(isin.trim().toUpperCase()));
        }

        /**
         * Resolves multiple trading symbols from Market Data service dynamically in a single batch query.
         * Uses the same {@code POST /v1/securities/batch-search} path as am-trade-management
         * (proven for ISIN → ticker), not {@code /v1/instruments/search}.
         *
         * @param isins list of ISIN codes to resolve
         * @return a Mono containing a map of ISIN to resolved trading symbol
         */
        @SuppressWarnings({ "rawtypes", "unchecked" })
        public Mono<Map> resolveTickersByIsins(List<String> isins) {
                log.info("Resolving batch of {} ticker symbols by ISINs via securities batch-search", isins.size());
                com.portfolio.marketdata.model.BatchSearchRequest request = com.portfolio.marketdata.model.BatchSearchRequest
                                .builder()
                                .queries(isins)
                                .limit(1)
                                .searchFields(List.of("ISIN"))
                                .minMatchScore(0.0)
                                .build();
                return batchSearch(request)
                                .map(response -> {
                                        Map<String, String> resultMap = new java.util.HashMap<>();
                                        if (response != null && response.getResults() != null) {
                                                for (com.portfolio.marketdata.model.BatchSearchResponse.QueryResult qr : response
                                                                .getResults()) {
                                                        if (qr == null || qr.getQuery() == null
                                                                        || qr.getMatches() == null
                                                                        || qr.getMatches().isEmpty()) {
                                                                continue;
                                                        }
                                                        com.portfolio.marketdata.model.BatchSearchResponse.SecurityMatch match = qr
                                                                        .getMatches().get(0);
                                                        String ticker = match.getSymbol();
                                                        if (ticker == null || ticker.isBlank()) {
                                                                continue;
                                                        }
                                                        // Key by the query ISIN the caller sent (stable for batch map lookup)
                                                        String isinKey = qr.getQuery().trim().toUpperCase();
                                                        resultMap.put(isinKey, ticker.trim().toUpperCase());
                                                }
                                        }
                                        return (Map) resultMap;
                                })
                                .doOnSuccess(data -> log.debug("Successfully resolved batch of {} ISINs",
                                                data != null ? data.size() : 0))
                                .doOnError(e -> log.error("Failed to resolve batch of ISINs: {}", e.getMessage()));
        }

        /**
         * Generic securities batch-search (SYMBOL / NAME / ISIN) keyed by the original query.
         */
        @SuppressWarnings({ "rawtypes", "unchecked" })
        public Mono<Map> resolveTickersByQueries(List<String> queries, List<String> searchFields) {
                if (queries == null || queries.isEmpty()) {
                        return Mono.just(Map.of());
                }
                List<String> fields = (searchFields == null || searchFields.isEmpty())
                                ? List.of("SYMBOL", "NAME")
                                : searchFields;
                log.info("Resolving batch of {} queries via securities batch-search fields={}", queries.size(), fields);
                com.portfolio.marketdata.model.BatchSearchRequest request = com.portfolio.marketdata.model.BatchSearchRequest
                                .builder()
                                .queries(queries)
                                .limit(1)
                                .searchFields(fields)
                                .minMatchScore(0.0)
                                .build();
                return batchSearch(request)
                                .map(response -> {
                                        Map<String, String> resultMap = new java.util.HashMap<>();
                                        if (response != null && response.getResults() != null) {
                                                for (com.portfolio.marketdata.model.BatchSearchResponse.QueryResult qr : response
                                                                .getResults()) {
                                                        if (qr == null || qr.getQuery() == null
                                                                        || qr.getMatches() == null
                                                                        || qr.getMatches().isEmpty()) {
                                                                continue;
                                                        }
                                                        com.portfolio.marketdata.model.BatchSearchResponse.SecurityMatch match = qr
                                                                        .getMatches().get(0);
                                                        String ticker = match.getSymbol();
                                                        if (ticker == null || ticker.isBlank()) {
                                                                continue;
                                                        }
                                                        resultMap.put(qr.getQuery().trim().toUpperCase(),
                                                                        ticker.trim().toUpperCase());
                                                }
                                        }
                                        return (Map) resultMap;
                                })
                                .doOnError(e -> log.error("Failed to resolve batch queries: {}", e.getMessage()));
        }
}

