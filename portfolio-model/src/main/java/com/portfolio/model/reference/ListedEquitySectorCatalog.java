package com.portfolio.model.reference;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * BSE equity master (symbol and ISIN to sector). Used when live market-data
 * stores a blank or "-" sector for a listed name such as IDEA.
 */
public final class ListedEquitySectorCatalog {

    public record Profile(String symbol, String isin, String sector, String industry) {
    }

    private static final String RESOURCE = "/bse-equity.csv";

    private static volatile Map<String, Profile> bySymbol = Map.of();
    private static volatile Map<String, Profile> byIsin = Map.of();
    private static volatile boolean loaded;

    private ListedEquitySectorCatalog() {
    }

    public static Profile find(String symbol, String isin) {
        ensureLoaded();
        String isinKey = normalize(isin);
        if (isinKey != null) {
            Profile byId = byIsin.get(isinKey);
            if (byId != null) {
                return byId;
            }
        }
        String symbolKey = normalize(symbol);
        if (symbolKey == null) {
            return null;
        }
        return bySymbol.get(symbolKey);
    }

    private static void ensureLoaded() {
        if (loaded) {
            return;
        }
        synchronized (ListedEquitySectorCatalog.class) {
            if (loaded) {
                return;
            }
            Map<String, Profile> symbols = new HashMap<>();
            Map<String, Profile> isins = new HashMap<>();
            try (InputStream in = ListedEquitySectorCatalog.class.getResourceAsStream(RESOURCE)) {
                if (in != null) {
                    try (BufferedReader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
                        String line = reader.readLine();
                        while ((line = reader.readLine()) != null) {
                            if (line.isBlank()) {
                                continue;
                            }
                            List<String> cols = splitCsv(line);
                            if (cols.size() < 11) {
                                continue;
                            }
                            String status = cols.get(4).trim();
                            if (!status.equalsIgnoreCase("Active")) {
                                continue;
                            }
                            String sector = usable(cols.get(10));
                            if (sector == null) {
                                continue;
                            }
                            String sym = normalize(cols.get(2));
                            String id = normalize(cols.get(7));
                            if (sym == null && id == null) {
                                continue;
                            }
                            Profile profile = new Profile(sym, id, sector, usable(cols.get(8)));
                            if (sym != null) {
                                symbols.putIfAbsent(sym, profile);
                            }
                            if (id != null) {
                                isins.putIfAbsent(id, profile);
                            }
                        }
                    }
                }
            } catch (Exception ignored) {
                symbols.clear();
                isins.clear();
            }
            bySymbol = Collections.unmodifiableMap(symbols);
            byIsin = Collections.unmodifiableMap(isins);
            loaded = true;
        }
    }

    static String usable(String raw) {
        if (raw == null) {
            return null;
        }
        String t = raw.trim();
        if (t.isEmpty() || "-".equals(t) || "unknown".equalsIgnoreCase(t)) {
            return null;
        }
        return t;
    }

    private static String normalize(String raw) {
        String t = usable(raw);
        if (t == null) {
            return null;
        }
        return t.toUpperCase(Locale.ROOT);
    }

    static List<String> splitCsv(String line) {
        java.util.ArrayList<String> cols = new java.util.ArrayList<>();
        StringBuilder cell = new StringBuilder();
        boolean quoted = false;
        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);
            if (c == '"') {
                quoted = !quoted;
                continue;
            }
            if (c == ',' && !quoted) {
                cols.add(cell.toString());
                cell.setLength(0);
                continue;
            }
            cell.append(c);
        }
        cols.add(cell.toString());
        return cols;
    }
}
