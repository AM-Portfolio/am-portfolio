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
         * @param symbols   the symbols to get OHLC data for
         * @param timeFrame the time frame for the OHLC data
         * @param refresh   whether to refresh the data or use cached data
         * @return a Mono of MarketDataResponseWrapper
         */
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
}
