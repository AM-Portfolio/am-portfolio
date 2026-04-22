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

    @Value("${etf.url:http://localhost:8022}")
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

    @Value("${market-data.api.base-url}")
    private String marketDataUrl;

    private List<SecurityDocumentDTO> referenceSecurities = null;

    private void fetchReferenceSecurities() {
        if (referenceSecurities != null && !referenceSecurities.isEmpty()) {
            return;
        }
        try {
            String url = String.format("%s/v1/securities/search", marketDataUrl);
            log.info("Fetching reference securities (NIFTY 500) from: {}", url);

            SecuritySearchRequest request = new SecuritySearchRequest();
            request.setIndex("NIFTY 500");

            // Assuming the response is a List<SecurityDocumentDTO>
            // We need to map it properly. RestTemplate might need
            // ParameterizedTypeReference but array works too.
            SecurityDocumentDTO[] response = restTemplate.postForObject(url, request, SecurityDocumentDTO[].class);

            if (response != null) {
                referenceSecurities = java.util.Arrays.asList(response);
                log.info("Loaded {} reference securities for fuzzy matching", referenceSecurities.size());
            }

        } catch (Exception e) {
            log.error("Failed to fetch reference securities: {}", e.getMessage());
        }
    }

    private SecurityDocumentDTO findBestMatch(String rawName, List<SecurityDocumentDTO> candidates) {
        if (rawName == null || candidates == null)
            return null;

        String normalizedRaw = normalize(rawName);

        // Strategy 1: Exact match on normalized name
        for (SecurityDocumentDTO doc : candidates) {
            if (doc.getMetadata() != null && doc.getMetadata().getCompanyName() != null) {
                String normalizedCandidate = normalize(doc.getMetadata().getCompanyName());
                if (normalizedRaw.equals(normalizedCandidate)) {
                    return doc;
                }
            }
        }

        // Strategy 2: Contains match (if one contains the other and length difference
        // is small)
        for (SecurityDocumentDTO doc : candidates) {
            if (doc.getMetadata() != null && doc.getMetadata().getCompanyName() != null) {
                String normalizedCandidate = normalize(doc.getMetadata().getCompanyName());
                if (normalizedRaw.contains(normalizedCandidate) || normalizedCandidate.contains(normalizedRaw)) {
                    // Simple heuristic: if length ratio > 0.7
                    double ratio = (double) Math.min(normalizedRaw.length(), normalizedCandidate.length())
                            / Math.max(normalizedRaw.length(), normalizedCandidate.length());
                    if (ratio > 0.6) {
                        return doc;
                    }
                }
            }
        }

        return null;
    }

    private String normalize(String s) {
        if (s == null)
            return "";
        return s.toLowerCase()
                .replace("limited", "")
                .replace("ltd", "")
                .replace("corp", "")
                .replace("corporation", "")
                .replace("company", "")
                .replaceAll("[^a-z0-9]", "")
                .trim();
    }

    public void enrichHoldings(List<EtfHolding> holdings) {
        if (holdings == null || holdings.isEmpty())
            return;

        try {
            List<String> queries = holdings.stream()
                    .map(h -> (h.getIsin() != null && !h.getIsin().isEmpty()) ? h.getIsin() : h.getSymbol())
                    .collect(Collectors.toList());

            // Split into chunks if necessary, but for now assuming one batch is fine or
            // logic inside BatchSearch handles it
            BatchSearchRequest request = new BatchSearchRequest();
            request.setQueries(queries);
            request.setLimit(1); // We only need the best match

            String url = String.format("%s/v1/securities/batch-search", marketDataUrl);
            log.info("Enriching {} holdings via {}", holdings.size(), url);

            BatchSearchResponse response = restTemplate.postForObject(url, request, BatchSearchResponse.class);
            Map<String, SecurityMatch> matchMap = new java.util.HashMap<>();

            if (response != null && response.getResults() != null) {
                for (BatchSearchResult result : response.getResults()) {
                    if (result.getMatches() != null && !result.getMatches().isEmpty()) {
                        matchMap.put(result.getQuery(), result.getMatches().get(0));
                    }
                }
            }

            // Check if we need fallback
            boolean needsFallback = holdings.stream().anyMatch(h -> {
                String query = (h.getIsin() != null && !h.getIsin().isEmpty()) ? h.getIsin() : h.getSymbol();
                SecurityMatch match = matchMap.get(query);
                return match == null || (h.getIsin() == null && match.getIsin() == null); // If we still don't have ISIN
            });

            if (needsFallback) {
                fetchReferenceSecurities();
            }

            for (EtfHolding h : holdings) {
                String query = (h.getIsin() != null && !h.getIsin().isEmpty()) ? h.getIsin() : h.getSymbol();
                SecurityMatch match = matchMap.get(query);

                // Fallback Logic
                if (match == null && referenceSecurities != null) {
                    SecurityDocumentDTO bestMatch = findBestMatch(h.getSymbol(), referenceSecurities); // h.getSymbol is
                                                                                                       // stockName if
                                                                                                       // ISIN missing
                    if (bestMatch != null) {
                        log.info("Fuzzy matched '{}' to '{}' ({})", h.getSymbol(),
                                bestMatch.getMetadata().getCompanyName(), bestMatch.getKey().getSymbol());
                        match = new SecurityMatch();
                        match.setSymbol(bestMatch.getKey().getSymbol());
                        match.setIsin(bestMatch.getKey().getIsin());
                        match.setSector(bestMatch.getMetadata().getSector());
                        match.setIndustry(bestMatch.getMetadata().getIndustry());
                        match.setMarketCapType(bestMatch.getMetadata().getMarketCapType());
                        if (bestMatch.getMetadata().getMarketCapValue() != null)
                            match.setMarketCapValue(Double.valueOf(bestMatch.getMetadata().getMarketCapValue()));
                    }
                }

                if (match != null) {
                    // CRITICAL: Update symbol from market data match (e.g., "Indusind Bank Ltd." ->
                    // "INDUSINDBK")
                    if (match.getSymbol() != null)
                        h.setSymbol(match.getSymbol());

                    if (match.getIsin() != null)
                        h.setIsin(match.getIsin());

                    if (match.getIndustry() != null) {
                        h.setSector(match.getIndustry());
                    } else if (match.getSector() != null) {
                        h.setSector(match.getSector());
                    }

                    // Fix for Market Cap mapping
                    // The field in EtfHolding is marketCapCategory (String) and marketCapValue
                    // (Double)
                    // The field in SecurityMatch is marketCapType (String) and marketCapValue
                    // (Double)
                    if (match.getMarketCapType() != null) {
                        h.setMarketCapCategory(match.getMarketCapType());
                    }
                    if (match.getMarketCapValue() != null) {
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
        private String isin; // Added ISIN
        private String sector;
        private String industry;
        private String marketCapType;
        private Double marketCapValue;
    }

    // Fallback DTOs
    @Data
    private static class SecuritySearchRequest {
        private String index;
        private List<String> symbols;
        private String query;
        private String sector;
        private String industry;
    }

    @Data
    private static class SecurityDocumentDTO {
        private SecurityKey key;
        private SecurityMetadata metadata;

        @Data
        public static class SecurityKey {
            private String symbol;
            private String isin;
        }

        @Data
        public static class SecurityMetadata {
            private String companyName;
            private String sector;
            private String industry;
            private Long marketCapValue;
            private String marketCapType;
        }
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
