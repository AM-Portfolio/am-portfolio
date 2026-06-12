package com.portfolio.basket.client;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.portfolio.basket.model.EtfData;
import com.portfolio.basket.model.EtfHolding;
import jakarta.annotation.PostConstruct;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

@Component
@Slf4j
public class EtfApiClient {

    private static final int BATCH_SEARCH_CHUNK_SIZE = 50;

    /** Canonical ETF per index query when multiple trackers match the same index name. */
    private static final Map<String, String> PREFERRED_SYMBOL_BY_INDEX = Map.of(
            "nifty 50", "NIFTYBEES",
            "nifty bank", "BANKBEES",
            "nifty it", "ITBEES");

    @Value("${etf.url:http://localhost:8022}")
    private String apiUrl;

    @Value("${market-data.api.base-url}")
    private String marketDataUrl;

    @Value("${basket.holdings.enrichment.enabled:true}")
    private boolean holdingsEnrichmentEnabled;

    @Value("${market-data.client.connect-timeout-ms:5000}")
    private int marketConnectTimeoutMs;

    @Value("${market-data.client.read-timeout-ms:45000}")
    private int marketReadTimeoutMs;

    private RestTemplate etfRestTemplate;
    private RestTemplate marketRestTemplate;
    private final Map<String, EtfData> etfCache = new java.util.concurrent.ConcurrentHashMap<>();

    @PostConstruct
    void initRestTemplates() {
        etfRestTemplate = new RestTemplate(clientFactory(60_000, 120_000));
        marketRestTemplate = new RestTemplate(clientFactory(marketConnectTimeoutMs, marketReadTimeoutMs));
    }

    private static SimpleClientHttpRequestFactory clientFactory(int connectMs, int readMs) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(connectMs);
        factory.setReadTimeout(readMs);
        return factory;
    }

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
        return etfRestTemplate.postForObject(url, entity, HoldingsLookupResponse.class);
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
     * Batch lookup for index names, symbols, or ISINs.
     * Index names (e.g. "Nifty IT") are resolved via GET /v1/etf/search first, then holdings by symbol.
     */
    public Map<String, EtfData> fetchEtfHoldingsBatch(List<String> items) {
        Map<String, EtfData> out = new LinkedHashMap<>();
        if (items == null || items.isEmpty()) {
            return out;
        }
        try {
            Map<String, String> queryToSymbol = new LinkedHashMap<>();
            List<String> symbolsForHoldings = new ArrayList<>();
            for (String item : items) {
                if (item == null || item.isBlank()) {
                    continue;
                }
                String key = item.trim();
                String symbol = resolveToSymbol(key);
                if (symbol == null) {
                    log.warn("Could not resolve ETF query '{}' to a symbol", key);
                    continue;
                }
                queryToSymbol.put(key, symbol);
                if (!symbolsForHoldings.contains(symbol)) {
                    symbolsForHoldings.add(symbol);
                }
            }
            if (symbolsForHoldings.isEmpty()) {
                return out;
            }
            HoldingsLookupResponse response = lookupHoldings(symbolsForHoldings);
            Map<String, EtfData> bySymbol = indexHoldingsBySymbol(response);
            for (Map.Entry<String, String> entry : queryToSymbol.entrySet()) {
                EtfData data = bySymbol.get(entry.getValue().toUpperCase(Locale.ROOT));
                if (data == null) {
                    data = resolveEtfForInput(entry.getValue(), response);
                }
                if (data != null) {
                    out.put(entry.getKey(), data);
                    log.info("Resolved query '{}' -> symbol {} ({} holdings)", entry.getKey(), data.getSymbol(),
                            data.getHoldings() != null ? data.getHoldings().size() : 0);
                    if (data.getSymbol() != null && !data.getSymbol().isBlank()) {
                        out.putIfAbsent(data.getSymbol(), data);
                    }
                }
            }
            indexEtfsByIsinAndSymbol(response, out);
        } catch (Exception e) {
            log.error("Failed batch ETF holdings lookup: {}", e.getMessage());
        }
        return out;
    }

    /** Resolve index name via parser search, or pass through symbol/ISIN. */
    public String resolveToSymbol(String query) {
        if (query == null || query.isBlank()) {
            return null;
        }
        String trimmed = query.trim();
        if (isDirectHoldingsKey(trimmed)) {
            return trimmed.toUpperCase(Locale.ROOT);
        }
        return resolveQueryToSymbol(trimmed);
    }

    private boolean isDirectHoldingsKey(String value) {
        if (value.contains(" ")) {
            return false;
        }
        if (value.matches("(?i)^INF[A-Z0-9]{10}$")) {
            return true;
        }
        return value.matches("^[A-Za-z0-9._-]+$") && value.length() <= 24;
    }

    public String resolveQueryToSymbol(String query) {
        try {
            String encoded = URLEncoder.encode(query, StandardCharsets.UTF_8);
            String url = apiUrl + "/v1/etf/search?query=" + encoded + "&limit=20";
            log.info("ETF search: {}", url);
            EtfSearchResponse response = etfRestTemplate.getForObject(url, EtfSearchResponse.class);
            if (response == null || response.getEtfs() == null || response.getEtfs().isEmpty()) {
                log.warn("ETF search returned no results for '{}'", query);
                return null;
            }
            List<String> symbols = response.getEtfs().stream()
                    .map(EtfInfo::getSymbol)
                    .filter(s -> s != null && !s.isBlank())
                    .collect(Collectors.toList());
            log.info("ETF search '{}' -> symbols {}", query, symbols);
            String preferred = PREFERRED_SYMBOL_BY_INDEX.get(query.toLowerCase(Locale.ROOT).trim());
            if (preferred != null) {
                for (EtfInfo etf : response.getEtfs()) {
                    if (preferred.equalsIgnoreCase(etf.getSymbol())) {
                        return preferred;
                    }
                }
            }
            return response.getEtfs().get(0).getSymbol();
        } catch (Exception e) {
            log.error("ETF search failed for '{}': {}", query, e.getMessage());
            return null;
        }
    }

    private Map<String, EtfData> indexHoldingsBySymbol(HoldingsLookupResponse response) {
        Map<String, EtfData> bySymbol = new LinkedHashMap<>();
        if (response == null || response.getEtfs() == null) {
            return bySymbol;
        }
        for (EtfApiResponse etf : response.getEtfs()) {
            EtfData data = toEtfData(etf);
            if (data != null && etf.getSymbol() != null && !etf.getSymbol().isBlank()) {
                bySymbol.put(etf.getSymbol().toUpperCase(Locale.ROOT), data);
            }
        }
        return bySymbol;
    }

    private EtfData mapFirstEtfFromResponse(String input, HoldingsLookupResponse response) {
        return resolveEtfForInput(input, response);
    }

    private EtfData resolveEtfForInput(String input, HoldingsLookupResponse response) {
        if (response == null || response.getEtfs() == null || response.getEtfs().isEmpty()) {
            log.warn("Empty holdings lookup response for: {}", input);
            return null;
        }
        String needle = input == null ? "" : input.trim();
        List<EtfApiResponse> candidates = filterEtfsForQuery(response.getEtfs(), needle);
        if (candidates.isEmpty()) {
            if (response.getTotalFound() != null && response.getTotalFound() == 1) {
                return toEtfData(response.getEtfs().get(0));
            }
            log.warn("No ETF match for '{}' among {} ETFs in batch", input, response.getEtfs().size());
            return null;
        }
        return toEtfData(pickRepresentativeEtf(candidates, needle));
    }

    /** Same matching rules as am-parser search: substring on symbol, name, or ISIN. */
    private List<EtfApiResponse> filterEtfsForQuery(List<EtfApiResponse> etfs, String query) {
        if (etfs == null || query == null || query.isBlank()) {
            return Collections.emptyList();
        }
        List<EtfApiResponse> matched = new ArrayList<>();
        for (EtfApiResponse etf : etfs) {
            if (matchesInput(etf, query)) {
                matched.add(etf);
            }
        }
        if (!matched.isEmpty()) {
            return matched;
        }
        String queryLower = query.toLowerCase(Locale.ROOT).trim();
        for (EtfApiResponse etf : etfs) {
            if (etf.getSymbol() != null && etf.getSymbol().toLowerCase(Locale.ROOT).contains(queryLower)) {
                matched.add(etf);
            } else if (etf.getName() != null && etf.getName().toLowerCase(Locale.ROOT).contains(queryLower)) {
                matched.add(etf);
            } else if (etf.getIsin() != null && etf.getIsin().toLowerCase(Locale.ROOT).contains(queryLower)) {
                matched.add(etf);
            }
        }
        return matched;
    }

    private EtfApiResponse pickRepresentativeEtf(List<EtfApiResponse> candidates, String query) {
        if (candidates.size() == 1) {
            return candidates.get(0);
        }
        String preferred = PREFERRED_SYMBOL_BY_INDEX.get(query.toLowerCase(Locale.ROOT).trim());
        if (preferred != null) {
            for (EtfApiResponse etf : candidates) {
                if (etf.getSymbol() != null && preferred.equalsIgnoreCase(etf.getSymbol())) {
                    return etf;
                }
            }
        }
        return candidates.get(0);
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
        if (etf.getSymbol() != null && input.equalsIgnoreCase(etf.getSymbol())) {
            return true;
        }
        String inputLower = input.toLowerCase(Locale.ROOT);
        if (etf.getName() != null) {
            String nameLower = etf.getName().toLowerCase(Locale.ROOT);
            int idx = nameLower.indexOf(inputLower);
            if (idx >= 0 && !nameContinuesPastQuery(nameLower, idx, inputLower.length())) {
                return true;
            }
        }
        return false;
    }

    /** Avoid matching "Nifty 50" to "Nifty 500" when the name continues with another digit. */
    private boolean nameContinuesPastQuery(String nameLower, int matchStart, int queryLen) {
        int end = matchStart + queryLen;
        if (end >= nameLower.length()) {
            return false;
        }
        char next = nameLower.charAt(end);
        if (!Character.isDigit(next)) {
            return false;
        }
        return queryLen > 0 && Character.isDigit(nameLower.charAt(end - 1));
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
        String symbol = resolveQueryToSymbol(query);
        if (symbol != null) {
            return List.of(symbol);
        }
        return Collections.emptyList();
    }

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

            SecurityDocumentDTO[] response = marketRestTemplate.postForObject(url, request, SecurityDocumentDTO[].class);

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
        if (holdings == null || holdings.isEmpty()) {
            return;
        }
        if (!holdingsEnrichmentEnabled) {
            log.debug("Skipping holdings enrichment (basket.holdings.enrichment.enabled=false)");
            return;
        }

        List<EtfHolding> needsIsin = holdings.stream()
                .filter(h -> !hasValidIsin(h))
                .collect(Collectors.toList());
        if (needsIsin.isEmpty()) {
            log.debug("All {} ETF constituents already have ISINs", holdings.size());
            return;
        }

        try {
            Map<String, SecurityMatch> matchMap = batchSearchByStockNames(needsIsin);
            if (matchMap.isEmpty()) {
                log.warn(
                        "Market batch-search returned no matches ({}). Check MARKET_DATA_API_URL={} is reachable from this machine.",
                        needsIsin.size(), marketDataUrl);
                return;
            }

            for (EtfHolding h : needsIsin) {
                String query = h.getSymbol();
                if (query == null || query.isBlank()) {
                    continue;
                }
                SecurityMatch match = matchMap.get(query);
                if (match == null) {
                    continue;
                }
                applySecurityMatch(h, match);
            }
            log.info("Enriched {}/{} ETF constituents with ISINs via market batch-search", matchMap.size(), needsIsin.size());
        } catch (Exception e) {
            log.warn("Market batch-search failed ({}). Basket overlap may be weaker without constituent ISINs.", e.getMessage());
        }
    }

    private boolean hasValidIsin(EtfHolding h) {
        return h.getIsin() != null && h.getIsin().length() >= 10 && !"-".equals(h.getIsin());
    }

    private Map<String, SecurityMatch> batchSearchByStockNames(List<EtfHolding> holdings) {
        List<String> names = holdings.stream()
                .map(EtfHolding::getSymbol)
                .filter(s -> s != null && !s.isBlank())
                .distinct()
                .collect(Collectors.toList());
        if (names.isEmpty()) {
            return Collections.emptyMap();
        }

        String url = marketDataUrl + "/v1/securities/batch-search";
        Map<String, SecurityMatch> matchMap = new LinkedHashMap<>();

        for (int i = 0; i < names.size(); i += BATCH_SEARCH_CHUNK_SIZE) {
            List<String> chunk = names.subList(i, Math.min(i + BATCH_SEARCH_CHUNK_SIZE, names.size()));
            BatchSearchRequest request = new BatchSearchRequest();
            request.setQueries(chunk);
            request.setLimit(1);
            request.setMinMatchScore(0.7);

            log.info("Enriching {} holdings via {}", chunk.size(), url);
            BatchSearchResponse response = marketRestTemplate.postForObject(url, request, BatchSearchResponse.class);
            if (response == null || response.getResults() == null) {
                continue;
            }
            for (BatchSearchResult result : response.getResults()) {
                if (result.getMatches() != null && !result.getMatches().isEmpty()) {
                    matchMap.put(result.getQuery(), result.getMatches().get(0));
                }
            }
        }
        return matchMap;
    }

    private void applySecurityMatch(EtfHolding h, SecurityMatch match) {
        if (match.getSymbol() != null) {
            h.setSymbol(match.getSymbol());
        }
        if (match.getIsin() != null) {
            h.setIsin(match.getIsin());
        }
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
        private Double minMatchScore;
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
        private String name;
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
