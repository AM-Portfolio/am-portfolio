package com.portfolio.marketdata.service;

import com.portfolio.marketdata.model.MutualFundNavMark;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import jakarta.annotation.PreDestroy;
import java.net.URI;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Scheme EOD NAV for heatmap / holdings.
 * Fast path: one AMFI bulk feed for all ISINs (+ prior day once).
 * Optional: am-parser catalog enrich (names / override) with a hard request budget.
 */
@Service
@Slf4j
public class MutualFundNavService {
    private static final DateTimeFormatter AMFI_DATE = DateTimeFormatter.ofPattern("dd-MMM-yyyy", Locale.US);
    private static final DateTimeFormatter ISO_DATE = DateTimeFormatter.ISO_LOCAL_DATE;
    private static final ParameterizedTypeReference<Map<String, Object>> MAP_TYPE =
            new ParameterizedTypeReference<>() {};
    private static final long CATALOG_BUDGET_MS = 1_800L;

    private final RestTemplate restTemplate;
    private final RestTemplate catalogRestTemplate;
    private final Map<String, MutualFundNavMark> catalogByIsin = new ConcurrentHashMap<>();
    private volatile Map<String, MutualFundNavMark> latestByIsin = Map.of();
    private volatile LocalDate latestFeedDate;
    private volatile long latestLoadedAt;
    private volatile boolean previousSnapshotLoaded;
    private final Map<LocalDate, Map<String, Double>> historyByDate = new ConcurrentHashMap<>();
    private final AtomicBoolean historyRefreshRunning = new AtomicBoolean();
    private final ExecutorService lookupExecutor = Executors.newFixedThreadPool(4, task -> {
        Thread thread = new Thread(task, "mf-nav-lookup");
        thread.setDaemon(true);
        return thread;
    });

    @Value("${mf.catalog.api.url:}")
    private String catalogBaseUrl;

    @Value("${mf.catalog.api.amfi-latest-url:https://portal.amfiindia.com/spages/NAVAll.txt}")
    private String latestUrl;

    @Value("${mf.catalog.api.amfi-history-url:https://portal.amfiindia.com/DownloadNAVHistoryReport_Po.aspx}")
    private String historyUrl;

    public MutualFundNavService() {
        this(createRestTemplate(2500, 8000), createRestTemplate(800, 1500));
    }

    MutualFundNavService(RestTemplate restTemplate) {
        this(restTemplate, restTemplate);
    }

    MutualFundNavService(RestTemplate restTemplate, RestTemplate catalogRestTemplate) {
        this.restTemplate = restTemplate;
        this.catalogRestTemplate = catalogRestTemplate;
    }

    private static RestTemplate createRestTemplate(int connectMs, int readMs) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(connectMs);
        factory.setReadTimeout(readMs);
        return new RestTemplate(factory);
    }

    /** Returns a NAV mark by either growth/payout or reinvestment scheme ISIN. */
    public MutualFundNavMark getMark(String isin) {
        Map<String, MutualFundNavMark> marks = getMarks(List.of(isin));
        String key = normalize(isin);
        return key == null ? null : marks.get(key);
    }

    /**
     * Batch NAV for scheme ISINs. AMFI bulk is the request-path source of truth for speed;
     * catalog may enrich scheme names inside a short budget without blocking overview.
     */
    public Map<String, MutualFundNavMark> getMarks(Iterable<String> isins) {
        if (isins == null) return Map.of();
        List<String> keys = new ArrayList<>();
        for (String raw : isins) {
            String key = normalize(raw);
            if (key != null && key.startsWith("INF") && !keys.contains(key)) {
                keys.add(key);
            }
        }
        if (keys.isEmpty()) return Map.of();

        Map<String, MutualFundNavMark> snapshot = loadLatest(true);
        ensurePreviousSnapshotReady();

        Map<String, MutualFundNavMark> result = new HashMap<>();
        for (String key : keys) {
            MutualFundNavMark cachedCatalog = catalogByIsin.get(key);
            if (cachedCatalog != null && cachedCatalog.nav() > 0) {
                result.put(key, withPrevious(cachedCatalog));
                continue;
            }
            MutualFundNavMark amfi = snapshot.get(key);
            if (amfi != null) {
                result.put(key, withPrevious(amfi));
            }
        }

        // Marks can lag the feed max date (e.g. feed max=21, scheme NAV=20).
        // Ensure history exists for each mark's own prior day, then re-attach.
        ensureHistoryForMarkDates(result.values());
        result.replaceAll((isin, mark) -> withPrevious(mark));

        enrichFromCatalogAsyncBudget(keys, result);
        return Map.copyOf(result);
    }

    private void enrichFromCatalogAsyncBudget(List<String> keys, Map<String, MutualFundNavMark> result) {
        if (!catalogEnabled() || keys.isEmpty()) return;
        // Prefer catalog scheme names when the budget allows; skip ISINs already catalog-cached.
        List<String> missingNames = keys.stream()
                .filter(key -> !catalogByIsin.containsKey(key))
                .limit(12)
                .toList();
        if (missingNames.isEmpty()) return;

        long deadline = System.currentTimeMillis() + CATALOG_BUDGET_MS;
        List<CompletableFuture<MutualFundNavMark>> futures = missingNames.stream()
                .map(isin -> CompletableFuture.supplyAsync(() -> fetchCatalogMark(isin), lookupExecutor))
                .toList();
        try {
            CompletableFuture.allOf(futures.toArray(CompletableFuture[]::new))
                    .get(Math.max(50, deadline - System.currentTimeMillis()), TimeUnit.MILLISECONDS);
        } catch (Exception e) {
            log.debug("MF catalog enrich budget elapsed: {}", e.getMessage());
        }
        for (int i = 0; i < missingNames.size(); i++) {
            String key = missingNames.get(i);
            MutualFundNavMark catalog = futures.get(i).getNow(null);
            if (catalog == null || catalog.nav() <= 0) continue;
            MutualFundNavMark existing = result.get(key);
            String name = catalog.schemeName() != null && !catalog.schemeName().isBlank()
                    ? catalog.schemeName()
                    : (existing != null ? existing.schemeName() : null);
            Double previous = catalog.previousNav() != null
                    ? catalog.previousNav()
                    : (existing != null ? existing.previousNav() : null);
            double nav = catalog.nav() > 0 ? catalog.nav() : (existing != null ? existing.nav() : 0);
            LocalDate navDate = catalog.navDate() != null
                    ? catalog.navDate()
                    : (existing != null ? existing.navDate() : null);
            if (nav <= 0) continue;
            MutualFundNavMark merged = withPrevious(
                    new MutualFundNavMark(key, name, nav, previous, navDate));
            catalogByIsin.put(key, merged);
            result.put(key, merged);
        }
    }

    private boolean catalogEnabled() {
        return catalogBaseUrl != null && !catalogBaseUrl.isBlank();
    }

    private MutualFundNavMark fetchCatalogMark(String isin) {
        try {
            URI detailUri = UriComponentsBuilder
                    .fromUriString(trimSlash(catalogBaseUrl) + "/v1/mutual-fund/{isin}")
                    .buildAndExpand(isin)
                    .encode()
                    .toUri();
            ResponseEntity<Map<String, Object>> response = catalogRestTemplate.exchange(
                    detailUri, HttpMethod.GET, null, MAP_TYPE);
            MutualFundNavMark latest = parseCatalogDetail(isin, response.getBody());
            if (latest == null) return null;
            Double previous = fetchCatalogPrevious(isin, latest.navDate());
            MutualFundNavMark mark = previous == null
                    ? latest
                    : new MutualFundNavMark(latest.isin(), latest.schemeName(), latest.nav(), previous, latest.navDate());
            catalogByIsin.put(isin, mark);
            return mark;
        } catch (Exception e) {
            log.debug("MF catalog NAV unavailable for {}: {}", isin, e.getMessage());
            return null;
        }
    }

    private Double fetchCatalogPrevious(String isin, LocalDate navDate) {
        if (navDate == null) return null;
        LocalDate from = navDate.minusDays(14);
        try {
            URI historyUri = UriComponentsBuilder
                    .fromUriString(trimSlash(catalogBaseUrl) + "/v1/mutual-fund/{isin}/history")
                    .queryParam("from", from.format(ISO_DATE))
                    .queryParam("to", navDate.format(ISO_DATE))
                    .buildAndExpand(isin)
                    .encode()
                    .toUri();
            ResponseEntity<Map<String, Object>> response = catalogRestTemplate.exchange(
                    historyUri, HttpMethod.GET, null, MAP_TYPE);
            return previousFromHistoryPoints(response.getBody(), navDate);
        } catch (Exception e) {
            log.debug("MF catalog history unavailable for {}: {}", isin, e.getMessage());
            return null;
        }
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

    private Map<String, MutualFundNavMark> loadLatest(boolean forceRefresh) {
        long age = System.currentTimeMillis() - latestLoadedAt;
        if (!forceRefresh && !latestByIsin.isEmpty() && age < TimeUnit.HOURS.toMillis(6)) return latestByIsin;
        if (latestLoadedAt != 0 && age < TimeUnit.MINUTES.toMillis(2)) return latestByIsin;
        synchronized (this) {
            age = System.currentTimeMillis() - latestLoadedAt;
            if (latestLoadedAt != 0 && age < (forceRefresh ? TimeUnit.HOURS.toMillis(6) : TimeUnit.MINUTES.toMillis(2))) {
                return latestByIsin;
            }
            try {
                String body = restTemplate.getForObject(latestUrl, String.class);
                Map<String, MutualFundNavMark> parsed = parseLatest(body);
                if (!parsed.isEmpty()) {
                    latestByIsin = Map.copyOf(parsed);
                    if (!latestFeedDateEquals(parsed, latestFeedDate)) previousSnapshotLoaded = false;
                    latestFeedDate = parsed.values().stream().map(MutualFundNavMark::navDate)
                            .filter(date -> date != null).max(LocalDate::compareTo).orElse(null);
                    historyByDate.keySet().removeIf(date -> latestFeedDate != null && date.isBefore(latestFeedDate.minusDays(8)));
                }
                latestLoadedAt = System.currentTimeMillis();
            } catch (Exception e) {
                latestLoadedAt = System.currentTimeMillis();
                log.warn("AMFI latest NAV feed unavailable: {}", e.getMessage());
            }
            return latestByIsin;
        }
    }

    private void ensurePreviousSnapshotReady() {
        if (previousSnapshotLoaded || latestFeedDate == null) return;
        if (historyRefreshRunning.compareAndSet(false, true)) {
            try {
                loadPreviousBusinessDay();
            } finally {
                historyRefreshRunning.set(false);
            }
            return;
        }
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(2);
        while (!previousSnapshotLoaded && System.nanoTime() < deadline) {
            try {
                Thread.sleep(20);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            }
        }
    }

    private void loadPreviousBusinessDay() {
        if (loadHistoryBefore(latestFeedDate)) {
            previousSnapshotLoaded = true;
        }
    }

    /**
     * Prefetch AMFI history for every calendar day in the T−1 window before [navDate].
     * Do not stop on the first non-empty day: weekend/holiday feeds are often thin
     * (hundreds of schemes) and would hide the real prior business day.
     */
    private boolean loadHistoryBefore(LocalDate navDate) {
        if (navDate == null) return false;
        boolean any = false;
        // Load past the selectAdjacent gap so a Sun→Fri jump still lands in cache.
        int windowDays = Math.max(MAX_PREVIOUS_GAP_DAYS, 5);
        for (int days = 1; days <= windowDays; days++) {
            LocalDate candidate = navDate.minusDays(days);
            Map<String, Double> values = historyByDate.get(candidate);
            if (values == null) {
                try {
                    String date = candidate.format(AMFI_DATE);
                    URI uri = UriComponentsBuilder.fromUriString(historyUrl)
                            .queryParam("frmdt", date).queryParam("todt", date)
                            .build().encode().toUri();
                    values = parseHistory(restTemplate.getForObject(uri, String.class));
                    historyByDate.put(candidate, values != null ? values : Map.of());
                } catch (Exception e) {
                    log.debug("AMFI NAV history unavailable for {}: {}", candidate, e.getMessage());
                    historyByDate.put(candidate, Map.of());
                    continue;
                }
            }
            if (values != null && !values.isEmpty()) {
                log.info("Loaded AMFI previous NAV snapshot for {} ({} schemes)", candidate, values.size());
                any = true;
            }
        }
        return any;
    }

    private void ensureHistoryForMarkDates(Iterable<MutualFundNavMark> marks) {
        if (marks == null) return;
        for (MutualFundNavMark mark : marks) {
            if (mark == null || mark.navDate() == null || mark.previousNav() != null) continue;
            if (previousNavFromHistory(mark) != null) continue;
            loadHistoryBefore(mark.navDate());
        }
    }

    private static boolean latestFeedDateEquals(Map<String, MutualFundNavMark> marks, LocalDate current) {
        if (current == null) return false;
        return marks.values().stream().map(MutualFundNavMark::navDate).anyMatch(current::equals);
    }

    /** Max calendar gap (incl. weekend/holiday) between NAV date and prior published day. */
    static final int MAX_PREVIOUS_GAP_DAYS = 3;
    /** Reject bogus 1D moves (wrong ISIN/history); open-end schemes do not move this much in 1D. */
    static final double MAX_ABS_1D_PCT = 10.0;

    private MutualFundNavMark withPrevious(MutualFundNavMark mark) {
        if (mark == null || mark.navDate() == null) {
            return mark;
        }
        Double previous = mark.previousNav();
        if (previous == null || previous <= 0) {
            previous = previousNavFromHistory(mark);
        }
        if (previous == null || previous <= 0) {
            return mark.previousNav() == null
                    ? mark
                    : new MutualFundNavMark(mark.isin(), mark.schemeName(), mark.nav(), null, mark.navDate());
        }
        if (!isPlausible1dMove(mark.nav(), previous)) {
            log.warn(
                    "Dropping implausible MF 1D NAV move isin={} nav={} previous={} (>{})",
                    mark.isin(),
                    mark.nav(),
                    previous,
                    MAX_ABS_1D_PCT);
            return new MutualFundNavMark(mark.isin(), mark.schemeName(), mark.nav(), null, mark.navDate());
        }
        if (mark.previousNav() != null && Double.compare(mark.previousNav(), previous) == 0) {
            return mark;
        }
        return new MutualFundNavMark(mark.isin(), mark.schemeName(), mark.nav(), previous, mark.navDate());
    }

    /**
     * Nearest history NAV strictly before the mark date, only if within
     * {@link #MAX_PREVIOUS_GAP_DAYS} (true prior business day, not a week-old fill).
     */
    private Double previousNavFromHistory(MutualFundNavMark mark) {
        if (mark == null || mark.navDate() == null) {
            return null;
        }
        String isin = normalize(mark.isin());
        if (isin == null) {
            return null;
        }
        return selectAdjacentPreviousNav(mark.navDate(), isin, historyByDate);
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

    static boolean isPlausible1dMove(double nav, double previous) {
        if (previous <= 0 || nav <= 0) {
            return false;
        }
        double pct = Math.abs((nav - previous) / previous) * 100.0;
        return pct <= MAX_ABS_1D_PCT;
    }

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

    /**
     * AMFI history rows come in two layouts:
     * legacy: code;name;isinGrowth;isinReinvest;nav;repurchase;sale;date
     * current: code;name;plan;option;isinGrowth;isinReinvest;nav;date
     */
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

    @PreDestroy
    void stopLookupExecutor() { lookupExecutor.shutdownNow(); }
}
