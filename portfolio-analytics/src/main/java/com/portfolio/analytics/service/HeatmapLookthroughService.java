package com.portfolio.analytics.service;

import com.portfolio.basket.client.EtfApiClient;
import com.portfolio.basket.model.EtfData;
import com.portfolio.basket.model.EtfHolding;
import com.portfolio.basket.service.EnrichedEtfService;
import com.portfolio.basket.util.SectorNormalizer;
import com.portfolio.marketdata.model.MutualFundNavMark;
import com.portfolio.marketdata.service.MutualFundNavService;
import com.portfolio.model.analytics.lookthrough.HeatmapLookthroughResponse;
import com.portfolio.model.analytics.lookthrough.HeatmapLookthroughResponse.Coverage;
import com.portfolio.model.analytics.lookthrough.HeatmapLookthroughResponse.HoldingSlice;
import com.portfolio.model.analytics.lookthrough.HeatmapLookthroughResponse.SectorSlice;
import com.portfolio.model.analytics.lookthrough.HeatmapLookthroughResponse.Source;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;

/**
 * Resolves ETF or mutual-fund constituents behind one heatmap scheme tile.
 * Ownership stays on the controller. Successful books are cached in memory for 24h.
 */
@Service
@Slf4j
public class HeatmapLookthroughService {

    static final int TOP_N = 40;
    private static final List<String> LOOKUP_PRODUCT_TYPES = List.of("ETF", "MUTUAL_FUND");
    private static final Set<String> WEIGHT_KEYS = Set.of(
            "percentage_to_nav", "percentage", "weight", "weight_pct", "weightPct");
    private static final Set<String> NAME_KEYS = Set.of(
            "name_of_instrument", "stock_name", "company_name", "instrument_name", "name");
    private static final Set<String> ISIN_KEYS = Set.of("isin_code", "isin", "isinCode");
    private static final Set<String> SYMBOL_KEYS = Set.of("symbol", "ticker", "nse_symbol", "stock_name");
    private static final Set<String> SECTOR_KEYS = Set.of("sector", "industry");
    private static final Set<String> CAP_KEYS = Set.of(
            "market_cap_category", "marketCapCategory", "market_cap_type", "marketCapType");
    private static final Set<String> FUND_NAME_KEYS = Set.of(
            "fund_name", "mutual_fund_name", "schemeName", "scheme_name");
    private static final Set<String> DATE_KEYS = Set.of(
            "portfolio_date", "latest_portfolio_date", "as_of", "asOf", "navDate");
    private static final Set<String> ID_KEYS = Set.of("id", "_id", "portfolio_id", "portfolioId");

    private final EnrichedEtfService enrichedEtfService;
    @Nullable
    private final EtfApiClient etfApiClient;
    @Nullable
    private final MutualFundNavService mutualFundNavService;
    private final RestTemplate restTemplate;
    private final Cache<String, ResolvedBook> cache = Caffeine.newBuilder()
            .maximumSize(2_000)
            .expireAfterWrite(24, TimeUnit.HOURS)
            .build();

    @Value("${etf.url:http://localhost:8022}")
    private String etfUrl = "http://localhost:8022";

    @Autowired
    public HeatmapLookthroughService(
            EnrichedEtfService enrichedEtfService,
            @Autowired(required = false) @Nullable EtfApiClient etfApiClient,
            @Autowired(required = false) @Nullable MutualFundNavService mutualFundNavService) {
        this.enrichedEtfService = enrichedEtfService;
        this.etfApiClient = etfApiClient;
        this.mutualFundNavService = mutualFundNavService;
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(2_000);
        factory.setReadTimeout(4_000);
        this.restTemplate = new RestTemplate(factory);
    }

    public HeatmapLookthroughResponse lookthrough(String schemeIsin, Double schemeValue) {
        String isin = normalize(schemeIsin);
        if (isin == null) {
            return unavailable(null, schemeValue);
        }
        ResolvedBook cached = cache.getIfPresent(isin);
        if (cached != null) {
            return toResponse(isin, schemeValue, cached);
        }
        ResolvedBook book = resolve(isin);
        if (book == null || book.holdings().isEmpty()) {
            return unavailable(isin, schemeValue);
        }
        cache.put(isin, book);
        log.info("Heatmap lookthrough isin={} source={} holdings={}", isin, book.source(), book.holdings().size());
        return toResponse(isin, schemeValue, book);
    }

    private ResolvedBook resolve(String isin) {
        // Scheme MF ISINs (INF*) must hit parser MUTUAL_FUND before ETF enrichment.
        if (com.portfolio.model.portfolio.HoldingInstrumentClassifier.isSchemeMfIsin(isin)) {
            ResolvedBook mf = fromLookup(isin, List.of("MUTUAL_FUND"));
            if (mf != null) {
                return mf;
            }
            return fromMutualFund(isin);
        }
        ResolvedBook enriched = fromEnriched(isin);
        if (enriched != null) {
            return enriched;
        }
        ResolvedBook etf = fromLookup(isin, List.of("ETF"));
        if (etf != null) {
            return etf;
        }
        ResolvedBook either = fromLookup(isin, LOOKUP_PRODUCT_TYPES);
        if (either != null) {
            return either;
        }
        return fromMutualFund(isin);
    }

    private ResolvedBook fromEnriched(String isin) {
        try {
            EtfData data = enrichedEtfService.getEnrichedEtf(isin);
            if (data == null || data.getHoldings() == null || data.getHoldings().isEmpty()) {
                return null;
            }
            return toBook(data, Source.ETF_HOLDINGS);
        } catch (RuntimeException e) {
            log.warn("Enriched ETF lookthrough failed for {}: {}", isin, e.getMessage());
            return null;
        }
    }

    private ResolvedBook fromLookup(String isin, List<String> productTypes) {
        if (etfApiClient == null) {
            return null;
        }
        try {
            EtfApiClient.HoldingsLookupResponse response =
                    etfApiClient.lookupHoldings(List.of(isin), productTypes);
            if (response == null || response.getEtfs() == null) {
                return null;
            }
            for (EtfApiClient.EtfApiResponse fund : response.getEtfs()) {
                if (fund.getHoldings() == null || fund.getHoldings().isEmpty()) {
                    continue;
                }
                EtfData data = etfApiClient.toEtfData(fund);
                if (data == null || data.getHoldings() == null || data.getHoldings().isEmpty()) {
                    continue;
                }
                return toBook(data, sourceFor(fund.getProductType()));
            }
        } catch (RuntimeException e) {
            log.warn("Holdings lookup lookthrough failed for {}: {}", isin, e.getMessage());
        }
        return null;
    }

    private ResolvedBook fromMutualFund(String isin) {
        if (mutualFundNavService == null) {
            return null;
        }
        String schemeName = null;
        String asOf = null;
        try {
            Map<String, MutualFundNavMark> marks = mutualFundNavService.getMarks(List.of(isin));
            MutualFundNavMark mark = marks.get(isin);
            if (mark == null && !marks.isEmpty()) {
                mark = marks.values().iterator().next();
            }
            if (mark != null) {
                schemeName = mark.schemeName();
                if (mark.navDate() != null) {
                    asOf = mark.navDate().toString();
                }
            }
        } catch (RuntimeException e) {
            log.warn("MF NAV mark failed for {}: {}", isin, e.getMessage());
        }
        if (schemeName == null || schemeName.isBlank()) {
            return null;
        }
        try {
            Map<String, Object> search = getQuiet(searchUri(schemeName));
            String fundName = fundNameFrom(search, schemeName);
            String portfolioDate = firstText(search, DATE_KEYS);
            List<RawHolding> holdings = search == null ? List.of() : extractHoldings(search);
            if (holdings.isEmpty()) {
                Map<String, Object> stats = getQuiet(statisticsUri(fundName));
                holdings = stats == null ? List.of() : extractHoldings(stats);
                String statsDate = firstText(stats, DATE_KEYS);
                if (statsDate != null) {
                    portfolioDate = statsDate;
                }
            }
            if (holdings.isEmpty()) {
                holdings = fetchFullPortfolio(search, fundName);
            }
            if (holdings.isEmpty()) {
                return null;
            }
            List<RawHolding> enriched = enrichRaw(holdings);
            if (enriched.isEmpty()) {
                return null;
            }
            String resolvedAsOf = portfolioDate != null ? portfolioDate : asOf;
            return new ResolvedBook(fundName, null, Source.MF_HOLDINGS, resolvedAsOf, enriched);
        } catch (RuntimeException e) {
            log.warn("MF lookthrough failed for {}: {}", isin, e.getMessage());
            return null;
        }
    }

    private List<RawHolding> fetchFullPortfolio(Map<String, Object> search, String fundName) {
        String id = firstId(search);
        if (id != null) {
            List<RawHolding> byId = holdingsAt(portfolioUri(id));
            if (!byId.isEmpty()) {
                return byId;
            }
        }
        Map<String, Object> listed = getQuiet(listUri(fundName));
        if (listed == null) {
            return List.of();
        }
        List<RawHolding> listedHoldings = extractHoldings(listed);
        if (!listedHoldings.isEmpty()) {
            return listedHoldings;
        }
        String listedId = firstId(listed);
        if (listedId == null || listedId.equals(id)) {
            return List.of();
        }
        return holdingsAt(portfolioUri(listedId));
    }

    private List<RawHolding> holdingsAt(URI uri) {
        Map<String, Object> body = getQuiet(uri);
        if (body == null) {
            return List.of();
        }
        return extractHoldings(body);
    }

    private ResolvedBook toBook(EtfData data, Source source) {
        List<RawHolding> raw = new ArrayList<>();
        for (EtfHolding holding : data.getHoldings()) {
            String symbol = holding.getSymbol();
            raw.add(new RawHolding(
                    holding.getIsin(),
                    symbol,
                    symbol,
                    holding.getSector(),
                    holding.getWeight(),
                    holding.getMarketCapCategory()));
        }
        List<RawHolding> enriched = enrichRaw(raw);
        if (enriched.isEmpty()) {
            return null;
        }
        return new ResolvedBook(data.getName(), data.getSymbol(), source, data.getReturnsAsOf(), enriched);
    }

    private List<RawHolding> enrichRaw(List<RawHolding> raw) {
        if (raw == null || raw.isEmpty()) {
            return List.of();
        }
        List<EtfHolding> holdings = new ArrayList<>(raw.size());
        for (RawHolding holding : raw) {
            EtfHolding copy = new EtfHolding();
            String symbol = holding.symbol();
            if (symbol == null || symbol.isBlank()) {
                symbol = holding.name();
            }
            copy.setIsin(holding.isin());
            copy.setSymbol(symbol);
            copy.setSector(holding.sector());
            copy.setWeight(holding.weightPct());
            copy.setMarketCapCategory(holding.marketCapCategory());
            holdings.add(copy);
        }
        if (etfApiClient != null && mostlyUnknown(holdings)) {
            try {
                etfApiClient.enrichHoldings(holdings);
            } catch (RuntimeException e) {
                log.warn("Lookthrough sector enrich failed: {}", e.getMessage());
            }
        }
        List<RawHolding> out = new ArrayList<>(holdings.size());
        for (int i = 0; i < holdings.size(); i++) {
            EtfHolding holding = holdings.get(i);
            RawHolding original = raw.get(i);
            String symbol = textOr(holding.getSymbol(), original.symbol());
            String name = original.name();
            if (name == null || name.isBlank() || name.equals(original.symbol())) {
                name = textOr(symbol, name);
            }
            out.add(new RawHolding(
                    textOr(holding.getIsin(), original.isin()),
                    symbol,
                    name,
                    textOr(holding.getSector(), original.sector()),
                    holding.getWeight(),
                    textOr(holding.getMarketCapCategory(), original.marketCapCategory())));
        }
        return out;
    }

    private static boolean mostlyUnknown(List<EtfHolding> holdings) {
        double total = 0;
        double unknown = 0;
        int unknownCount = 0;
        for (EtfHolding holding : holdings) {
            double weight = Math.max(holding.getWeight(), 0);
            total += weight;
            if (holding.getSector() == null
                    || holding.getSector().isBlank()
                    || SectorNormalizer.isUnknown(holding.getSector())) {
                unknown += weight;
                unknownCount++;
            }
        }
        if (total <= 0) {
            return unknownCount * 2 >= holdings.size();
        }
        return unknown / total >= 0.5;
    }

    private static HeatmapLookthroughResponse toResponse(String isin, Double schemeValue, ResolvedBook book) {
        return HeatmapLookthroughResponse.builder()
                .schemeIsin(isin)
                .schemeName(book.schemeName())
                .schemeSymbol(book.schemeSymbol())
                .source(book.source())
                .asOf(book.asOf())
                .available(true)
                .schemeValue(schemeValue)
                .coverage(coverage(book.holdings()))
                .sectors(group(book.holdings(), schemeValue))
                .build();
    }

    private static List<SectorSlice> group(List<RawHolding> holdings, Double schemeValue) {
        List<RawHolding> sorted = new ArrayList<>(holdings);
        sorted.sort(Comparator.comparingDouble(RawHolding::weightPct).reversed());
        int cap = Math.min(TOP_N, sorted.size());
        List<RawHolding> top = new ArrayList<>(sorted.subList(0, cap));
        List<RawHolding> rest = new ArrayList<>(sorted.subList(cap, sorted.size()));

        Map<String, List<RawHolding>> bySector = new LinkedHashMap<>();
        for (RawHolding holding : top) {
            bySector.computeIfAbsent(displaySector(holding.sector()), key -> new ArrayList<>()).add(holding);
        }
        List<SectorSlice> sectors = new ArrayList<>();
        for (Map.Entry<String, List<RawHolding>> entry : bySector.entrySet()) {
            sectors.add(toSector(entry.getKey(), entry.getValue(), schemeValue));
        }
        if (!rest.isEmpty()) {
            double restWeight = rest.stream().mapToDouble(RawHolding::weightPct).sum();
            addOther(sectors, restWeight, schemeValue);
        }
        sectors.sort(Comparator.comparing(SectorSlice::getWeightPct, Comparator.nullsLast(Comparator.reverseOrder())));
        return sectors;
    }

    private static void addOther(List<SectorSlice> sectors, double restWeight, Double schemeValue) {
        HoldingSlice synthetic = HoldingSlice.builder()
                .name("Other holdings")
                .weightPct(restWeight)
                .value(scale(schemeValue, restWeight))
                .build();
        for (int i = 0; i < sectors.size(); i++) {
            SectorSlice existing = sectors.get(i);
            if (!"Other".equals(existing.getSector())) {
                continue;
            }
            List<HoldingSlice> merged = new ArrayList<>(existing.getHoldings() == null
                    ? List.of()
                    : existing.getHoldings());
            merged.add(synthetic);
            merged.sort(Comparator.comparing(HoldingSlice::getWeightPct, Comparator.nullsLast(Comparator.reverseOrder())));
            double weight = (existing.getWeightPct() == null ? 0 : existing.getWeightPct()) + restWeight;
            sectors.set(i, SectorSlice.builder()
                    .sector("Other")
                    .weightPct(weight)
                    .value(sum(existing.getValue(), scale(schemeValue, restWeight)))
                    .holdings(merged)
                    .build());
            return;
        }
        sectors.add(SectorSlice.builder()
                .sector("Other")
                .weightPct(restWeight)
                .value(scale(schemeValue, restWeight))
                .holdings(List.of(synthetic))
                .build());
    }

    private static SectorSlice toSector(String sector, List<RawHolding> holdings, Double schemeValue) {
        List<HoldingSlice> slices = new ArrayList<>();
        double weight = 0;
        Double valueSum = null;
        for (RawHolding holding : holdings) {
            weight += holding.weightPct();
            Double value = scale(schemeValue, holding.weightPct());
            valueSum = sum(valueSum, value);
            String name = holding.name();
            if (name == null || name.isBlank()) {
                name = holding.symbol();
            }
            slices.add(HoldingSlice.builder()
                    .isin(blankToNull(holding.isin()))
                    .symbol(blankToNull(holding.symbol()))
                    .name(name)
                    .weightPct(holding.weightPct())
                    .value(value)
                    .marketCapCategory(blankToNull(holding.marketCapCategory()))
                    .build());
        }
        slices.sort(Comparator.comparing(HoldingSlice::getWeightPct, Comparator.nullsLast(Comparator.reverseOrder())));
        return SectorSlice.builder()
                .sector(sector)
                .weightPct(weight)
                .value(valueSum)
                .holdings(slices)
                .build();
    }

    private static Coverage coverage(List<RawHolding> holdings) {
        int count = holdings.size();
        int withIsin = 0;
        double total = 0;
        double unknown = 0;
        for (RawHolding holding : holdings) {
            total += holding.weightPct();
            if (holding.isin() != null && !holding.isin().isBlank()) {
                withIsin++;
            }
            if (holding.sector() == null
                    || holding.sector().isBlank()
                    || SectorNormalizer.isUnknown(holding.sector())) {
                unknown += holding.weightPct();
            }
        }
        double isinCoverage = count == 0 ? 0 : (double) withIsin / count;
        double unknownPct = total <= 0 ? 0 : unknown / total * 100.0;
        return Coverage.builder()
                .holdingsCount(count)
                .isinCoverage(isinCoverage)
                .unknownSectorWeightPct(unknownPct)
                .build();
    }

    private static HeatmapLookthroughResponse unavailable(String isin, Double schemeValue) {
        return HeatmapLookthroughResponse.builder()
                .schemeIsin(isin)
                .source(Source.UNAVAILABLE)
                .available(false)
                .schemeValue(schemeValue)
                .coverage(Coverage.builder()
                        .holdingsCount(0)
                        .isinCoverage(0)
                        .unknownSectorWeightPct(0)
                        .build())
                .sectors(List.of())
                .build();
    }

    private Map<String, Object> getQuiet(URI uri) {
        try {
            ResponseEntity<Map<String, Object>> response = restTemplate.exchange(
                    uri, HttpMethod.GET, null, new ParameterizedTypeReference<>() {});
            return response.getBody();
        } catch (RuntimeException e) {
            log.warn("Lookthrough GET {} failed: {}", uri, e.getMessage());
            return null;
        }
    }

    private URI searchUri(String fundName) {
        return UriComponentsBuilder.fromUriString(baseUrl() + "/v1/portfolios/search")
                .queryParam("fund_name", fundName)
                .build()
                .encode()
                .toUri();
    }

    private URI statisticsUri(String fundName) {
        return UriComponentsBuilder.fromUriString(baseUrl() + "/v1/funds/{name}/statistics")
                .buildAndExpand(fundName)
                .encode()
                .toUri();
    }

    private URI portfolioUri(String id) {
        return UriComponentsBuilder.fromUriString(baseUrl() + "/v1/portfolios/{id}")
                .buildAndExpand(id)
                .encode()
                .toUri();
    }

    private URI listUri(String fundName) {
        return UriComponentsBuilder.fromUriString(baseUrl() + "/v1/portfolios")
                .queryParam("fund_name", fundName)
                .queryParam("limit", 5)
                .build()
                .encode()
                .toUri();
    }

    private String baseUrl() {
        String base = etfUrl == null || etfUrl.isBlank() ? "http://localhost:8022" : etfUrl.trim();
        if (base.endsWith("/")) {
            return base.substring(0, base.length() - 1);
        }
        return base;
    }

    private static List<RawHolding> extractHoldings(Object node) {
        List<RawHolding> found = new ArrayList<>();
        collectHoldings(node, found);
        return found;
    }

    private static void collectHoldings(Object node, List<RawHolding> found) {
        if (node instanceof Map<?, ?> map) {
            RawHolding holding = asHolding(map);
            if (holding != null) {
                found.add(holding);
                return;
            }
            for (Object value : map.values()) {
                collectHoldings(value, found);
            }
            return;
        }
        if (node instanceof List<?> list) {
            for (Object item : list) {
                collectHoldings(item, found);
            }
        }
    }

    private static RawHolding asHolding(Map<?, ?> map) {
        Object weightRaw = first(map, WEIGHT_KEYS);
        if (weightRaw == null) {
            return null;
        }
        Double weight = parseWeight(weightRaw);
        if (weight == null) {
            return null;
        }
        String name = firstString(map, NAME_KEYS);
        String symbol = firstString(map, SYMBOL_KEYS);
        if ((name == null || name.isBlank()) && (symbol == null || symbol.isBlank())) {
            return null;
        }
        if (name == null || name.isBlank()) {
            name = symbol;
        }
        return new RawHolding(
                firstString(map, ISIN_KEYS),
                symbol,
                name,
                firstString(map, SECTOR_KEYS),
                weight,
                firstString(map, CAP_KEYS));
    }

    private static String fundNameFrom(Map<String, Object> body, String fallback) {
        String found = firstText(body, FUND_NAME_KEYS);
        return found == null || found.isBlank() ? fallback : found;
    }

    private static String firstText(Object node, Set<String> keys) {
        if (node instanceof Map<?, ?> map) {
            String direct = firstString(map, keys);
            if (direct != null) {
                return direct;
            }
            for (Object value : map.values()) {
                String nested = firstText(value, keys);
                if (nested != null) {
                    return nested;
                }
            }
        } else if (node instanceof List<?> list) {
            for (Object item : list) {
                String nested = firstText(item, keys);
                if (nested != null) {
                    return nested;
                }
            }
        }
        return null;
    }

    private static String firstId(Object node) {
        if (!(node instanceof Map<?, ?> map)) {
            if (node instanceof List<?> list) {
                for (Object item : list) {
                    String id = firstId(item);
                    if (id != null) {
                        return id;
                    }
                }
            }
            return null;
        }
        if (map.containsKey("fund_name") || map.containsKey("mutual_fund_name") || map.containsKey("portfolio")) {
            String id = firstString(map, ID_KEYS);
            if (id != null) {
                return id;
            }
        }
        for (Object value : map.values()) {
            String id = firstId(value);
            if (id != null) {
                return id;
            }
        }
        return null;
    }

    private static Object first(Map<?, ?> map, Set<String> keys) {
        for (Map.Entry<?, ?> entry : map.entrySet()) {
            if (entry.getKey() != null && keys.contains(String.valueOf(entry.getKey())) && entry.getValue() != null) {
                return entry.getValue();
            }
        }
        return null;
    }

    private static String firstString(Map<?, ?> map, Set<String> keys) {
        return asText(first(map, keys));
    }

    private static String asText(Object value) {
        if (value instanceof String text) {
            String trimmed = text.trim();
            return trimmed.isEmpty() ? null : trimmed;
        }
        if (value instanceof Number number) {
            return number.toString();
        }
        return null;
    }

    private static Double parseWeight(Object raw) {
        if (raw instanceof Number number) {
            double value = number.doubleValue();
            return Double.isNaN(value) ? null : value;
        }
        String text = asText(raw);
        if (text == null) {
            return null;
        }
        if (text.endsWith("%")) {
            text = text.substring(0, text.length() - 1).trim();
        }
        try {
            return Double.parseDouble(text.replace(",", ""));
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static Source sourceFor(String productType) {
        if (productType != null && productType.toUpperCase(Locale.ROOT).contains("MUTUAL")) {
            return Source.MF_HOLDINGS;
        }
        return Source.ETF_HOLDINGS;
    }

    private static String displaySector(String sector) {
        if (sector == null || sector.isBlank() || SectorNormalizer.isUnknown(sector)) {
            return "Unknown";
        }
        return sector.trim();
    }

    private static Double scale(Double schemeValue, double weightPct) {
        if (schemeValue == null || schemeValue <= 0) {
            return null;
        }
        return schemeValue * weightPct / 100.0;
    }

    private static Double sum(Double left, Double right) {
        if (left == null) {
            return right;
        }
        if (right == null) {
            return left;
        }
        return left + right;
    }

    private static String normalize(String schemeIsin) {
        if (schemeIsin == null) {
            return null;
        }
        String trimmed = schemeIsin.trim();
        if (trimmed.isEmpty()) {
            return null;
        }
        return trimmed.toUpperCase(Locale.ROOT);
    }

    private static String textOr(String preferred, String fallback) {
        if (preferred != null && !preferred.isBlank()) {
            return preferred;
        }
        return fallback;
    }

    private static String blankToNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value;
    }

    private record ResolvedBook(
            String schemeName,
            String schemeSymbol,
            Source source,
            String asOf,
            List<RawHolding> holdings) {
    }

    private record RawHolding(
            String isin,
            String symbol,
            String name,
            String sector,
            double weightPct,
            String marketCapCategory) {
    }
}
