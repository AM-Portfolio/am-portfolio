package com.portfolio.basket.client;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.portfolio.basket.model.EtfData;
import com.portfolio.basket.model.EtfHolding;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
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
    private final Map<String, EtfData> etfCache = new java.util.concurrent.ConcurrentHashMap<>();

    /**
     * POST /v1/etf/holdings — primary contract (symbol, ISIN, or name query per item).
     */
    public HoldingsLookupResponse lookupHoldings(List<String> items) {
        if (items == null || items.isEmpty()) {
            return new HoldingsLookupResponse(items, 0, Collections.emptyList(), Collections.emptyList());
        }
        String url = apiUrl + "/v1/etf/holdings";
        HoldingsLookupRequest request = new HoldingsLookupRequest(items);
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<HoldingsLookupRequest> entity = new HttpEntity<>(request, headers);
        log.info("POST ETF holdings lookup: {} items -> {}", items.size(), url);
        return restTemplate.postForObject(url, entity, HoldingsLookupResponse.class);
    }

    /**
     * Single ETF by symbol, ISIN, or name (uses first match when search returns one ETF).
     */
    public EtfData fetchEtfHoldings(String symbolOrIsin) {
        if (symbolOrIsin == null || symbolOrIsin.isBlank()) {
            return null;
        }
        if (etfCache.containsKey(symbolOrIsin)) {
            log.info("Returning cached ETF holdings for: {}", symbolOrIsin);
            return etfCache.get(symbolOrIsin);
        }
        try {
            HoldingsLookupResponse response = lookupHoldings(List.of(symbolOrIsin));
            EtfData data = mapFirstEtfFromResponse(symbolOrIsin, response);
            if (data != null) {
                etfCache.put(symbolOrIsin, data);
                if (data.getSymbol() != null && !data.getSymbol().isBlank()) {
                    etfCache.put(data.getSymbol(), data);
                }
            }
            return data;
        } catch (Exception e) {
            log.error("Failed to fetch ETF holdings for {}. Error: {}", symbolOrIsin, e.getMessage());
            log.debug("Stack Trace:", e);
        }
        return null;
    }

    /**
     * Batch lookup — one POST; response is a deduped union keyed by ISIN and symbol.
     */
    public Map<String, EtfData> fetchEtfHoldingsBatch(List<String> items) {
        Map<String, EtfData> out = new LinkedHashMap<>();
        if (items == null || items.isEmpty()) {
            return out;
        }
        try {
            HoldingsLookupResponse response = lookupHoldings(items);
            indexEtfsByIsinAndSymbol(response, out);
        } catch (Exception e) {
            log.error("Failed batch ETF holdings lookup: {}", e.getMessage());
        }
        return out;
    }

    private EtfData mapFirstEtfFromResponse(String input, HoldingsLookupResponse response) {
        if (response == null || response.getEtfs() == null || response.getEtfs().isEmpty()) {
            log.warn("Empty holdings lookup response for: {}", input);
            return null;
        }
        String needle = input == null ? "" : input.trim();
        for (EtfApiResponse etf : response.getEtfs()) {
            if (matchesInput(etf, needle)) {
                return toEtfData(etf);
            }
        }
        if (response.getTotalFound() != null && response.getTotalFound() == 1) {
            return toEtfData(response.getEtfs().get(0));
        }
        log.warn("No exact ETF match for '{}' in {} results", input, response.getTotalFound());
        return null;
    }

    private void indexEtfsByIsinAndSymbol(HoldingsLookupResponse response, Map<String, EtfData> out) {
        if (response == null || response.getEtfs() == null) {
            return;
        }
        for (EtfApiResponse etf : response.getEtfs()) {
            EtfData data = toEtfData(etf);
            if (data == null) {
                continue;
            }
            if (etf.getIsin() != null && !etf.getIsin().isBlank()) {
                out.putIfAbsent(etf.getIsin(), data);
            }
            if (etf.getSymbol() != null && !etf.getSymbol().isBlank()) {
                out.putIfAbsent(etf.getSymbol(), data);
            }
        }
    }

    private boolean matchesInput(EtfApiResponse etf, String input) {
        if (input == null || input.isBlank() || etf == null) {
            return false;
        }
        if (etf.getIsin() != null && input.equalsIgnoreCase(etf.getIsin())) {
            return true;
        }
        return etf.getSymbol() != null && input.equalsIgnoreCase(etf.getSymbol());
    }

    private EtfData toEtfData(EtfApiResponse response) {
        if (response == null || response.getHoldings() == null) {
            return null;
        }
        EtfData data = new EtfData();
        data.setName(response.getName());
        data.setSymbol(response.getSymbol());

        List<EtfHolding> holdings = response.getHoldings().stream()
                .map(h -> {
                    EtfHolding holding = new EtfHolding();
                    holding.setIsin(h.getIsinCode());
                    holding.setSymbol(h.getStockName());
                    holding.setSector("Unknown");
                    holding.setWeight(h.getPercentage() != null ? h.getPercentage() : 0.0);
                    return holding;
                })
                .collect(Collectors.toList());

        data.setHoldings(holdings);
        log.info(
                "Mapped ETF {} (isin={}), holdings count={}",
                data.getSymbol(),
                response.getIsin(),
                holdings.size());
        return data;
    }

    public List<String> searchEtfs(String query) {
        try {
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

        for (SecurityDocumentDTO doc : candidates) {
            if (doc.getMetadata() != null && doc.getMetadata().getCompanyName() != null) {
                String normalizedCandidate = normalize(doc.getMetadata().getCompanyName());
                if (normalizedRaw.equals(normalizedCandidate)) {
                    return doc;
                }
            }
        }

        for (SecurityDocumentDTO doc : candidates) {
            if (doc.getMetadata() != null && doc.getMetadata().getCompanyName() != null) {
                String normalizedCandidate = normalize(doc.getMetadata().getCompanyName());
                if (normalizedRaw.contains(normalizedCandidate) || normalizedCandidate.contains(normalizedRaw)) {
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

            BatchSearchRequest request = new BatchSearchRequest();
            request.setQueries(queries);
            request.setLimit(1);

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

            boolean needsFallback = holdings.stream().anyMatch(h -> {
                String query = (h.getIsin() != null && !h.getIsin().isEmpty()) ? h.getIsin() : h.getSymbol();
                SecurityMatch match = matchMap.get(query);
                return match == null || (h.getIsin() == null && match.getIsin() == null);
            });

            if (needsFallback) {
                fetchReferenceSecurities();
            }

            for (EtfHolding h : holdings) {
                String query = (h.getIsin() != null && !h.getIsin().isEmpty()) ? h.getIsin() : h.getSymbol();
                SecurityMatch match = matchMap.get(query);

                if (match == null && referenceSecurities != null) {
                    SecurityDocumentDTO bestMatch = findBestMatch(h.getSymbol(), referenceSecurities);
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
                    if (match.getSymbol() != null)
                        h.setSymbol(match.getSymbol());

                    if (match.getIsin() != null)
                        h.setIsin(match.getIsin());

                    if (match.getIndustry() != null) {
                        h.setSector(match.getIndustry());
                    } else if (match.getSector() != null) {
                        h.setSector(match.getSector());
                    }

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
    public static class HoldingsLookupRequest {
        private List<String> items;

        public HoldingsLookupRequest(List<String> items) {
            this.items = new ArrayList<>(items);
        }
    }

    @Data
    public static class HoldingsLookupResponse {
        private List<String> items;
        @JsonProperty("total_found")
        private Integer totalFound;
        private List<EtfApiResponse> etfs;
        @JsonProperty("not_found")
        private List<String> notFound;

        public HoldingsLookupResponse() {
        }

        public HoldingsLookupResponse(
                List<String> items,
                Integer totalFound,
                List<EtfApiResponse> etfs,
                List<String> notFound) {
            this.items = items;
            this.totalFound = totalFound;
            this.etfs = etfs;
            this.notFound = notFound;
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
        private String isin;
        private String sector;
        private String industry;
        private String marketCapType;
        private Double marketCapValue;
    }

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
    public static class EtfApiResponse {
        private String symbol;
        private String name;
        private String isin;
        @JsonProperty("asset_class")
        private String assetClass;
        @JsonProperty("market_cap_category")
        private String marketCapCategory;
        private List<ApiHolding> holdings;
        @JsonProperty("holdings_count")
        private Integer holdingsCount;
        private String message;
    }

    @Data
    private static class ApiHolding {
        @JsonProperty("stock_name")
        private String stockName;

        @JsonProperty("isin_code")
        private String isinCode;

        private Double percentage;

        @JsonProperty("market_value")
        private Double marketValue;

        private Double quantity;
    }
}
