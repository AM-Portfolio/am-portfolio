package com.portfolio.marketdata.service;

import com.portfolio.marketdata.model.MutualFundNavMark;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.net.URI;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

/**
 * Scheme EOD NAV for heatmap / holdings.
 * Primary: one am-parser {@code POST /v1/mutual-fund/nav/batch} per book.
 * Does not download AMFI {@code NAVAll.txt} on the request path.
 */
@Service
@Slf4j
public class MutualFundNavService {
    private static final DateTimeFormatter AMFI_DATE = DateTimeFormatter.ofPattern("dd-MMM-yyyy", Locale.US);
    private static final DateTimeFormatter ISO_DATE = DateTimeFormatter.ISO_LOCAL_DATE;
    private static final ParameterizedTypeReference<Map<String, Object>> MAP_TYPE =
            new ParameterizedTypeReference<>() {};
    private static final long BATCH_CACHE_TTL_MS = TimeUnit.MINUTES.toMillis(10);

    private final RestTemplate catalogRestTemplate;
    private final Map<String, MutualFundNavMark> markByIsin = new ConcurrentHashMap<>();
    private volatile long batchLoadedAt;

    @Value("${mf.catalog.api.url:}")
    private String catalogBaseUrl;

    public MutualFundNavService() {
        this(createRestTemplate(800, 2500));
    }

    MutualFundNavService(RestTemplate catalogRestTemplate) {
        this.catalogRestTemplate = catalogRestTemplate;
    }

    /** Dual-template ctor kept for existing tests; only the catalog client is used. */
    MutualFundNavService(RestTemplate ignoredAmfi, RestTemplate catalogRestTemplate) {
        this(catalogRestTemplate);
    }

    private static RestTemplate createRestTemplate(int connectMs, int readMs) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(connectMs);
        factory.setReadTimeout(readMs);
        return new RestTemplate(factory);
    }

    public MutualFundNavMark getMark(String isin) {
        Map<String, MutualFundNavMark> marks = getMarks(List.of(isin));
        String key = normalize(isin);
        return key == null ? null : marks.get(key);
    }

    /**
     * Batch NAV for scheme ISINs via am-parser. Empty catalog URL → empty map
     * (callers fall back to CAS import marks).
     */
    public Map<String, MutualFundNavMark> getMarks(Iterable<String> isins) {
        if (isins == null) return Map.of();
        LinkedHashSet<String> keys = new LinkedHashSet<>();
        for (String raw : isins) {
            String key = normalize(raw);
            if (key != null && key.startsWith("INF")) {
                keys.add(key);
            }
        }
        if (keys.isEmpty()) return Map.of();

        if (!catalogEnabled()) {
            log.warn("MF catalog URL empty — no NAV marks (set MF_CATALOG_API_URL)");
            return Map.of();
        }

        long age = System.currentTimeMillis() - batchLoadedAt;
        Map<String, MutualFundNavMark> result = new HashMap<>();
        List<String> missing = new ArrayList<>();
        if (age < BATCH_CACHE_TTL_MS && !markByIsin.isEmpty()) {
            for (String key : keys) {
                MutualFundNavMark cached = markByIsin.get(key);
                if (cached != null && cached.nav() > 0) {
                    result.put(key, sanitize(cached));
                } else {
                    missing.add(key);
                }
            }
            if (missing.isEmpty()) {
                return Map.copyOf(result);
            }
        } else {
            missing.addAll(keys);
        }

        Map<String, MutualFundNavMark> fetched = fetchBatch(missing.isEmpty() ? keys : missing);
        if (!fetched.isEmpty()) {
            markByIsin.putAll(fetched);
            batchLoadedAt = System.currentTimeMillis();
            result.putAll(fetched);
        }
        for (String key : keys) {
            MutualFundNavMark mark = result.get(key);
            if (mark == null) {
                MutualFundNavMark cached = markByIsin.get(key);
                if (cached != null) {
                    result.put(key, sanitize(cached));
                }
            } else {
                result.put(key, sanitize(mark));
            }
        }
        return Map.copyOf(result);
    }

    private Map<String, MutualFundNavMark> fetchBatch(Iterable<String> isins) {
        List<String> list = new ArrayList<>();
        for (String isin : isins) {
            if (isin != null && !isin.isBlank()) list.add(isin);
        }
        if (list.isEmpty()) return Map.of();
        try {
            URI uri = URI.create(trimSlash(catalogBaseUrl) + "/v1/mutual-fund/nav/batch");
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.setAccept(List.of(MediaType.APPLICATION_JSON));
            Map<String, Object> body = Map.of("isins", list);
            ResponseEntity<Map<String, Object>> response = catalogRestTemplate.exchange(
                    uri, HttpMethod.POST, new HttpEntity<>(body, headers), MAP_TYPE);
            return parseBatchResponse(response.getBody());
        } catch (Exception e) {
            log.warn("MF catalog nav batch failed count={} err={}", list.size(), e.getMessage());
            return Map.of();
        }
    }

    static Map<String, MutualFundNavMark> parseBatchResponse(Map<String, Object> body) {
        Map<String, MutualFundNavMark> result = new HashMap<>();
        if (body == null || body.isEmpty()) return result;
        Object rawMarks = body.get("marks");
        if (!(rawMarks instanceof List<?> marks)) return result;
        for (Object raw : marks) {
            if (!(raw instanceof Map<?, ?> row)) continue;
            @SuppressWarnings("unchecked")
            Map<String, Object> markMap = (Map<String, Object>) row;
            MutualFundNavMark mark = parseBatchMark(markMap);
            if (mark != null) {
                result.put(mark.isin(), mark);
            }
        }
        return result;
    }

    static MutualFundNavMark parseBatchMark(Map<String, Object> body) {
        if (body == null || body.isEmpty()) return null;
        Double nav = asDouble(body.get("nav"));
        if (nav == null || nav <= 0) return null;
        String isin = normalize(asString(body.get("isin")));
        if (isin == null || !isin.startsWith("INF")) return null;
        String schemeName = asString(body.get("schemeName"));
        LocalDate navDate = parseIsoDate(asString(body.get("navDate")));
        Double previous = asDouble(body.get("previousNav"));
        return new MutualFundNavMark(isin, schemeName, nav, previous, navDate);
    }

    private MutualFundNavMark sanitize(MutualFundNavMark mark) {
        if (mark == null) return null;
        Double previous = mark.previousNav();
        if (previous == null || previous <= 0) {
            return mark.previousNav() == null
                    ? mark
                    : new MutualFundNavMark(mark.isin(), mark.schemeName(), mark.nav(), null, mark.navDate());
        }
        if (!isPlausible1dMove(mark.nav(), previous)) {
            log.warn(
                    "Dropping implausible MF 1D NAV move isin={} nav={} previous={} (>{}%)",
                    mark.isin(),
                    mark.nav(),
                    previous,
                    MAX_ABS_1D_PCT);
            return new MutualFundNavMark(mark.isin(), mark.schemeName(), mark.nav(), null, mark.navDate());
        }
        return mark;
    }

    private boolean catalogEnabled() {
        return catalogBaseUrl != null && !catalogBaseUrl.isBlank();
    }

    /** Max calendar gap (incl. weekend/holiday) between NAV date and prior published day. */
    static final int MAX_PREVIOUS_GAP_DAYS = 3;
    /** Reject bogus 1D moves (wrong ISIN/history); open-end schemes do not move this much in 1D. */
    static final double MAX_ABS_1D_PCT = 10.0;

    static boolean isPlausible1dMove(double nav, double previous) {
        if (previous <= 0 || nav <= 0) {
            return false;
        }
        double pct = Math.abs((nav - previous) / previous) * 100.0;
        return pct <= MAX_ABS_1D_PCT;
    }

    static MutualFundNavMark parseCatalogDetail(String isin, Map<String, Object> body) {
        if (body == null || body.isEmpty()) return null;
        Double nav = asDouble(body.get("nav"));
        if (nav == null || nav <= 0) return null;
        String schemeName = asString(body.get("schemeName"));
        LocalDate navDate = parseIsoDate(asString(body.get("navDate")));
        String resolvedIsin = normalize(asString(body.get("isin")));
        if (resolvedIsin == null) resolvedIsin = isin;
        return new MutualFundNavMark(resolvedIsin, schemeName, nav, null, navDate);
    }

    static Double previousFromHistoryPoints(Map<String, Object> body, LocalDate latestDate) {
        if (body == null) return null;
        Object rawPoints = body.get("points");
        if (!(rawPoints instanceof List<?> points) || points.isEmpty()) return null;
        LocalDate bestDate = null;
        Double bestNav = null;
        for (Object raw : points) {
            if (!(raw instanceof Map<?, ?> point)) continue;
            LocalDate date = parseIsoDate(asString(point.get("date")));
            Double nav = asDouble(point.get("nav"));
            if (date == null || nav == null || nav <= 0) continue;
            if (latestDate != null && !date.isBefore(latestDate)) continue;
            if (latestDate != null) {
                long gap = java.time.temporal.ChronoUnit.DAYS.between(date, latestDate);
                if (gap < 1 || gap > MAX_PREVIOUS_GAP_DAYS) continue;
            }
            if (bestDate == null || date.isAfter(bestDate)) {
                bestDate = date;
                bestNav = nav;
            }
        }
        return bestNav;
    }

    static Double selectAdjacentPreviousNav(
            LocalDate navDate, String isin, Map<LocalDate, Map<String, Double>> historyByDate) {
        if (navDate == null || isin == null || historyByDate == null || historyByDate.isEmpty()) {
            return null;
        }
        LocalDate bestDate = null;
        Double bestNav = null;
        for (Map.Entry<LocalDate, Map<String, Double>> entry : historyByDate.entrySet()) {
            LocalDate date = entry.getKey();
            if (date == null || !date.isBefore(navDate)) {
                continue;
            }
            long gap = java.time.temporal.ChronoUnit.DAYS.between(date, navDate);
            if (gap < 1 || gap > MAX_PREVIOUS_GAP_DAYS) {
                continue;
            }
            Map<String, Double> daily = entry.getValue();
            if (daily == null || daily.isEmpty()) {
                continue;
            }
            Double previous = daily.get(isin);
            if (previous == null || previous <= 0) {
                continue;
            }
            if (bestDate == null || date.isAfter(bestDate)) {
                bestDate = date;
                bestNav = previous;
            }
        }
        return bestNav;
    }

    /** Kept for unit tests of AMFI text layouts; not used on the live request path. */
    static Map<String, MutualFundNavMark> parseLatest(String body) {
        Map<String, MutualFundNavMark> result = new HashMap<>();
        if (body == null) return result;
        for (String line : body.split("\\R")) {
            String[] fields = line.split(";", -1);
            if (fields.length < 6 || !fields[0].trim().matches("\\d+")) continue;
            int navIndex = fields.length >= 8 ? 6 : 4;
            int dateIndex = fields.length >= 8 ? 7 : 5;
            Double nav = number(fields, navIndex);
            LocalDate date = parseDate(fields[dateIndex]);
            if (nav == null || date == null) continue;
            String name = fields.length >= 8 ? fields[3].trim() : fields[2].trim();
            MutualFundNavMark mark = new MutualFundNavMark("", name, nav, null, date);
            addIsin(result, fields[1], mark);
            if (fields.length >= 3) addIsin(result, fields[2], mark);
        }
        result.replaceAll((isin, mark) -> new MutualFundNavMark(isin, mark.schemeName(), mark.nav(), null, mark.navDate()));
        return result;
    }

    static Map<String, Double> parseHistory(String body) {
        Map<String, Double> result = new HashMap<>();
        if (body == null) return result;
        for (String line : body.split("\\R")) {
            String[] fields = line.split(";", -1);
            if (fields.length < 6 || !fields[0].trim().matches("\\d+")) continue;
            String isinGrowthNew = normalize(fields.length > 4 ? fields[4] : null);
            String isinGrowthLegacy = normalize(fields.length > 2 ? fields[2] : null);
            Double nav;
            String isinGrowth;
            String isinReinvest;
            if (isinGrowthNew != null && isinGrowthNew.startsWith("INF")) {
                nav = number(fields, 6);
                isinGrowth = isinGrowthNew;
                isinReinvest = normalize(fields.length > 5 ? fields[5] : null);
            } else if (isinGrowthLegacy != null && isinGrowthLegacy.startsWith("INF")) {
                nav = number(fields, 4);
                isinGrowth = isinGrowthLegacy;
                isinReinvest = normalize(fields.length > 3 ? fields[3] : null);
            } else {
                continue;
            }
            if (nav == null) continue;
            result.putIfAbsent(isinGrowth, nav);
            if (isinReinvest != null && isinReinvest.startsWith("INF")) {
                result.putIfAbsent(isinReinvest, nav);
            }
        }
        return Map.copyOf(result);
    }

    private static void addIsin(Map<String, MutualFundNavMark> target, String raw, MutualFundNavMark mark) {
        String isin = normalize(raw);
        if (isin != null && isin.startsWith("INF")) target.putIfAbsent(isin, mark);
    }

    private static Double number(String[] fields, int index) {
        if (index >= fields.length) return null;
        try {
            double value = Double.parseDouble(fields[index].trim());
            return value > 0 && Double.isFinite(value) ? value : null;
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static LocalDate parseDate(String raw) {
        try { return LocalDate.parse(raw.trim(), AMFI_DATE); }
        catch (DateTimeParseException e) { return null; }
    }

    private static LocalDate parseIsoDate(String raw) {
        if (raw == null || raw.isBlank()) return null;
        String trimmed = raw.trim();
        try {
            if (trimmed.length() >= 10) {
                return LocalDate.parse(trimmed.substring(0, 10), ISO_DATE);
            }
            return LocalDate.parse(trimmed, ISO_DATE);
        } catch (DateTimeParseException e) {
            return parseDate(trimmed);
        }
    }

    private static Double asDouble(Object raw) {
        if (raw instanceof Number number) {
            double value = number.doubleValue();
            return value > 0 && Double.isFinite(value) ? value : null;
        }
        if (raw instanceof String text && !text.isBlank()) {
            try {
                double value = Double.parseDouble(text.trim());
                return value > 0 && Double.isFinite(value) ? value : null;
            } catch (NumberFormatException e) {
                return null;
            }
        }
        return null;
    }

    private static String asString(Object raw) {
        return raw == null ? null : raw.toString().trim();
    }

    private static String normalize(String value) {
        return value == null || value.isBlank() || "-".equals(value.trim())
                ? null : value.trim().toUpperCase(Locale.ROOT);
    }

    private static String trimSlash(String base) {
        if (base == null) return "";
        return base.endsWith("/") ? base.substring(0, base.length() - 1) : base;
    }

    /** No-op kept for tests that previously shut down the AMFI lookup pool. */
    void stopLookupExecutor() {
        // intentional no-op
    }
}
