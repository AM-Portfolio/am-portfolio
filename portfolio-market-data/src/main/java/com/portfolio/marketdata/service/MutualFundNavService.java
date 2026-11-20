package com.portfolio.marketdata.service;

import com.portfolio.marketdata.model.MutualFundNavMark;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import jakarta.annotation.PreDestroy;
import java.net.URI;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
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

/** Reads official EOD scheme NAV from AMFI's bulk NAV feed (never equity quotes). */
@Service
@Slf4j
public class MutualFundNavService {
    private static final DateTimeFormatter AMFI_DATE = DateTimeFormatter.ofPattern("dd-MMM-yyyy", Locale.US);
    private final RestTemplate restTemplate;
    private volatile Map<String, MutualFundNavMark> latestByIsin = Map.of();
    private volatile LocalDate latestFeedDate;
    private volatile long latestLoadedAt;
    private volatile boolean previousSnapshotLoaded;
    private final Map<LocalDate, Map<String, Double>> historyByDate = new ConcurrentHashMap<>();
    private final AtomicBoolean historyRefreshRunning = new AtomicBoolean();
    private final ExecutorService lookupExecutor = Executors.newFixedThreadPool(2, task -> {
        Thread thread = new Thread(task, "mf-nav-amfi");
        thread.setDaemon(true);
        return thread;
    });

    @Value("${mf.catalog.api.amfi-latest-url:https://portal.amfiindia.com/spages/NAVAll.txt}")
    private String latestUrl;

    @Value("${mf.catalog.api.amfi-history-url:https://portal.amfiindia.com/DownloadNAVHistoryReport_Po.aspx}")
    private String historyUrl;

    public MutualFundNavService() {
        this(createRestTemplate());
    }

    MutualFundNavService(RestTemplate restTemplate) {
        this.restTemplate = restTemplate;
    }

    private static RestTemplate createRestTemplate() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(2500);
        factory.setReadTimeout(8000);
        return new RestTemplate(factory);
    }

    /** Returns a NAV mark by either growth/payout or reinvestment scheme ISIN. */
    public MutualFundNavMark getMark(String isin) {
        String key = normalize(isin);
        if (key == null || !key.startsWith("INF")) return null;
        Map<String, MutualFundNavMark> snapshot = loadLatest(false);
        MutualFundNavMark mark = snapshot.get(key);
        return mark == null ? null : withPrevious(mark);
    }

    /** Loads the AMFI bulk feed once and returns immediately while prior-day data warms in background. */
    public Map<String, MutualFundNavMark> getMarks(Iterable<String> isins) {
        Map<String, MutualFundNavMark> snapshot = loadLatest(true);
        if (latestFeedDate != null && !previousSnapshotLoaded && historyRefreshRunning.compareAndSet(false, true)) {
            CompletableFuture.runAsync(this::loadPreviousBusinessDay, lookupExecutor)
                    .whenComplete((ignored, error) -> {
                        historyRefreshRunning.set(false);
                        if (error != null) log.warn("AMFI previous NAV refresh failed: {}", error.getMessage());
                    });
        }
        Map<String, MutualFundNavMark> result = new HashMap<>();
        if (isins != null) {
            for (String raw : isins) {
                String key = normalize(raw);
                MutualFundNavMark mark = key == null ? null : snapshot.get(key);
                if (mark != null) result.put(key, withPrevious(mark));
            }
        }
        return Map.copyOf(result);
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

    private void loadPreviousBusinessDay() {
        LocalDate latestDate = latestFeedDate;
        if (latestDate == null) return;
        for (int days = 1; days <= 7; days++) {
            LocalDate candidate = latestDate.minusDays(days);
            Map<String, Double> values = historyByDate.get(candidate);
            if (values == null) {
                try {
                    String date = candidate.format(AMFI_DATE);
                    URI uri = UriComponentsBuilder.fromUriString(historyUrl)
                            .queryParam("frmdt", date).queryParam("todt", date)
                            .build().encode().toUri();
                    values = parseHistory(restTemplate.getForObject(uri, String.class));
                    historyByDate.put(candidate, values);
                } catch (Exception e) {
                    log.debug("AMFI NAV history unavailable for {}: {}", candidate, e.getMessage());
                    continue;
                }
            }
            if (!values.isEmpty()) {
                previousSnapshotLoaded = true;
                log.info("Loaded AMFI previous NAV snapshot for {} ({} schemes)", candidate, values.size());
                return;
            }
        }
    }

    private static boolean latestFeedDateEquals(Map<String, MutualFundNavMark> marks, LocalDate current) {
        if (current == null) return false;
        return marks.values().stream().map(MutualFundNavMark::navDate).anyMatch(current::equals);
    }

    private MutualFundNavMark withPrevious(MutualFundNavMark mark) {
        if (mark.navDate() == null || mark.previousNav() != null) return mark;
        for (int days = 1; days <= 7; days++) {
            Map<String, Double> daily = historyByDate.get(mark.navDate().minusDays(days));
            if (daily == null || daily.isEmpty()) continue;
            Double previous = daily.get(normalize(mark.isin()));
            if (previous != null && previous > 0) {
                return new MutualFundNavMark(mark.isin(), mark.schemeName(), mark.nav(), previous, mark.navDate());
            }
        }
        return mark;
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
        // Retain each key as the mark's ISIN for consumers that inspect the returned mark.
        result.replaceAll((isin, mark) -> new MutualFundNavMark(isin, mark.schemeName(), mark.nav(), null, mark.navDate()));
        return result;
    }

    static Map<String, Double> parseHistory(String body) {
        Map<String, Double> result = new HashMap<>();
        if (body == null) return result;
        for (String line : body.split("\\R")) {
            String[] fields = line.split(";", -1);
            if (fields.length < 8 || !fields[0].trim().matches("\\d+")) continue;
            Double nav = number(fields, 4);
            if (nav == null) continue;
            result.putIfAbsent(normalize(fields[2]), nav);
            result.putIfAbsent(normalize(fields[3]), nav);
        }
        result.remove(null);
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

    private static String normalize(String value) {
        return value == null || value.isBlank() || "-".equals(value.trim())
                ? null : value.trim().toUpperCase(Locale.ROOT);
    }

    @PreDestroy
    void stopLookupExecutor() { lookupExecutor.shutdownNow(); }
}
