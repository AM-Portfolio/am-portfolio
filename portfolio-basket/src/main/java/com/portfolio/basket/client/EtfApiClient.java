package com.portfolio.basket.client;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.portfolio.basket.model.EtfData;
import com.portfolio.basket.model.EtfHolding;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
@Slf4j
public class EtfApiClient {

    @Value("${etf.api.url:http://localhost:8022}")
    private String apiUrl;

    private final RestTemplate restTemplate = new RestTemplate();
    private final java.util.Map<String, EtfData> etfCache = new java.util.concurrent.ConcurrentHashMap<>();

    public EtfData fetchEtfHoldings(String symbolOrIsin) {
        if (etfCache.containsKey(symbolOrIsin)) {
            log.info("Returning cached ETF holdings for: {}", symbolOrIsin);
            return etfCache.get(symbolOrIsin);
        }
        try {
            String url = String.format("%s/v1/etf/holdings/%s", apiUrl, symbolOrIsin);
            log.info("Fetching ETF holdings from: {}", url);

            EtfApiResponse response = restTemplate.getForObject(url, EtfApiResponse.class);
            log.info("ETF API Response received: {}", response != null ? "Not Null" : "Null");

            if (response != null && response.getHoldings() != null) {
                log.info("ETF Holdings found. Count: {}, Name: {}", response.getHoldings().size(), response.getName());

                EtfData data = new EtfData();
                data.setName(response.getName());
                data.setSymbol(response.getSymbol());

                List<EtfHolding> holdings = response.getHoldings().stream()
                        .map(h -> {
                            EtfHolding holding = new EtfHolding();
                            holding.setIsin(h.getIsinCode());
                            holding.setSymbol(h.getStockName()); // Use Name as symbol if symbol missing in response
                            // API response might not have sector, default to Unknown if needed
                            holding.setSector("Unknown");
                            holding.setWeight(h.getPercentage() != null ? h.getPercentage() : 0.0);
                            return holding;
                        })
                        .collect(Collectors.toList());

                data.setHoldings(holdings);
                log.info("Successfully mapped ETF Data for {}. Holdings: {}", symbolOrIsin, holdings.size());
                etfCache.put(symbolOrIsin, data);
                return data;
            } else {
                log.warn("ETF Response was null or had no holdings for: {}", symbolOrIsin);
            }
        } catch (Exception e) {
            log.error("Failed to fetch ETF holdings for {}. Error: {}", symbolOrIsin, e.getMessage());
            log.debug("Stack Trace:", e);
        }
        return null;
    }

    public List<String> searchEtfs(String query) {
        try {
            // Updated to use the search endpoint with limit as suggested
            String url = String.format("%s/v1/etf/search?query=%s&limit=10", apiUrl, query);
            log.info("Searching ETFs with query: {}", url);

            EtfSearchResponse response = restTemplate.getForObject(url, EtfSearchResponse.class);
            if (response != null && response.getEtfs() != null) {
                List<String> isins = response.getEtfs().stream()
                        .map(EtfInfo::getIsin)
                        .filter(isin -> isin != null && !isin.isEmpty())
                        .collect(Collectors.toList());
                log.info("Found {} ETFs matching query: {}", isins.size(), query);
                return isins;
            }
        } catch (Exception e) {
            log.error("Failed to search ETFs: {}", e.getMessage());
        }
        return Collections.emptyList();
    }

    @Value("${market.data.api.base-url}")
    private String marketDataUrl;

    public void enrichHoldings(List<EtfHolding> holdings) {
        if (holdings == null || holdings.isEmpty())
            return;

        try {
            List<String> queries = holdings.stream()
                    .map(h -> h.getSymbol() != null ? h.getSymbol() : h.getIsin())
                    .collect(Collectors.toList());

            // Split into chunks if necessary, but for now assuming one batch is fine or
            // logic inside BatchSearch handles it
            BatchSearchRequest request = new BatchSearchRequest();
            request.setQueries(queries);
            request.setLimit(1); // We only need the best match

            String url = String.format("%s/v1/securities/batch-search", marketDataUrl);
            log.info("Enriching {} holdings via {}", holdings.size(), url);

            BatchSearchResponse response = restTemplate.postForObject(url, request, BatchSearchResponse.class);

            if (response != null && response.getResults() != null) {
                Map<String, SecurityMatch> matchMap = new java.util.HashMap<>();
                for (BatchSearchResult result : response.getResults()) {
                    if (result.getMatches() != null && !result.getMatches().isEmpty()) {
                        matchMap.put(result.getQuery(), result.getMatches().get(0));
                    }
                }

                for (EtfHolding h : holdings) {
                    String query = h.getSymbol() != null ? h.getSymbol() : h.getIsin();
                    SecurityMatch match = matchMap.get(query);
                    if (match != null) {
                        if (match.getSector() != null)
                            h.setSector(match.getSector());
                        if (match.getMarketCapType() != null)
                            h.setMarketCapCategory(match.getMarketCapType());
                        if (match.getMarketCapValue() != null)
                            h.setMarketCapValue(match.getMarketCapValue());
                    }
                }
            }

        } catch (Exception e) {
            log.error("Failed to enrich holdings: {}", e.getMessage());
        }
    }

    @Data
    private static class BatchSearchRequest {
        private List<String> queries;
        private Integer limit;
    }

    @Data
    private static class BatchSearchResponse {
        private List<BatchSearchResult> results;
    }

    @Data
    private static class BatchSearchResult {
        private String query;
        private List<SecurityMatch> matches;
    }

    @Data
    private static class SecurityMatch {
        private String symbol;
        private String sector;
        private String marketCapType;
        private Double marketCapValue;
    }

    @Data
    private static class EtfSearchResponse {
        private List<EtfInfo> etfs;
    }

    @Data
    private static class EtfInfo {
        private String isin;
        private String symbol;
    }

    @Data
    private static class EtfApiResponse {
        private String symbol;
        private String name;
        private String isin;
        private List<ApiHolding> holdings;
    }

    @Data
    private static class ApiHolding {
        @JsonProperty("stock_name")
        private String stockName;

        @JsonProperty("isin_code")
        private String isinCode;

        private Double percentage;
    }
}
