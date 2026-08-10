package com.portfolio.redis.service;

import com.am.common.amcommondata.document.asset.equity.EquityDocument;
import com.am.common.amcommondata.document.portfolio.PortfolioDocument;
import com.am.common.amcommondata.model.PortfolioModelV1;
import com.am.common.amcommondata.model.asset.equity.EquityModel;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Service;

import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Publishes tradable portfolio symbols into a shared Redis set consumed by am-market-data
 * ({@code SymbolOrchestratorService}) for previous-close and live stream universes.
 *
 * <p>Fail-open: Redis errors are logged and never fail portfolio saves.
 */
@Slf4j
@Service
public class ActiveMarketSymbolPublisher {

    public static final String DEFAULT_REDIS_KEY = "market:active-symbols";

    private static final Pattern ISIN_PATTERN = Pattern.compile("^[A-Z]{2}[A-Z0-9]{10}$");

    private final StringRedisTemplate stringRedisTemplate;

    @Value("${market.active-symbols.enabled:true}")
    private boolean enabled;

    @Value("${market.active-symbols.redis-key:" + DEFAULT_REDIS_KEY + "}")
    private String redisKey;

    public ActiveMarketSymbolPublisher(@Nullable StringRedisTemplate stringRedisTemplate) {
        this.stringRedisTemplate = stringRedisTemplate;
    }

    public void publishFromPortfolio(PortfolioModelV1 portfolio) {
        if (!enabled || portfolio == null || portfolio.getEquityModels() == null) {
            return;
        }
        Set<String> symbols = new HashSet<>();
        for (EquityModel equity : portfolio.getEquityModels()) {
            if (equity == null) {
                continue;
            }
            addCandidate(symbols, equity.getSymbol());
            addCandidate(symbols, equity.getIsin());
        }
        publishSymbols(symbols);
    }

    public void publishFromDocuments(Collection<PortfolioDocument> documents) {
        if (!enabled || documents == null || documents.isEmpty()) {
            return;
        }
        Set<String> symbols = new HashSet<>();
        for (PortfolioDocument doc : documents) {
            if (doc == null || doc.getEquities() == null) {
                continue;
            }
            for (EquityDocument equity : doc.getEquities()) {
                if (equity == null) {
                    continue;
                }
                addCandidate(symbols, equity.getSymbol());
                addCandidate(symbols, equity.getIsin());
            }
        }
        publishSymbols(symbols);
    }

    public void publishSymbols(Collection<String> symbols) {
        if (!enabled || symbols == null || symbols.isEmpty()) {
            return;
        }
        if (stringRedisTemplate == null) {
            log.warn("ActiveMarketSymbolPublisher: StringRedisTemplate unavailable; skipped {} symbols",
                    symbols.size());
            return;
        }
        try {
            String[] members = symbols.stream()
                    .filter(s -> s != null && !s.isBlank())
                    .map(s -> s.trim().toUpperCase(Locale.ROOT))
                    .distinct()
                    .toArray(String[]::new);
            if (members.length == 0) {
                return;
            }
            Long added = stringRedisTemplate.opsForSet().add(redisKey, members);
            log.info("ActiveMarketSymbolPublisher: SADD {} members into {} (new≈{})",
                    members.length, redisKey, added);
        } catch (Exception e) {
            log.warn("ActiveMarketSymbolPublisher: failed to publish symbols (fail-open): {}", e.getMessage());
        }
    }

    private static void addCandidate(Set<String> out, String raw) {
        if (raw == null || raw.isBlank()) {
            return;
        }
        String cleaned = raw.trim().toUpperCase(Locale.ROOT);
        if (cleaned.contains(":")) {
            cleaned = cleaned.substring(cleaned.indexOf(':') + 1).trim();
        }
        if (cleaned.contains("|")) {
            cleaned = cleaned.substring(cleaned.indexOf('|') + 1).trim();
        }
        if (!cleaned.isEmpty()) {
            out.add(cleaned);
        }
    }

    /** Exposed for tests / callers that want to know if a value looks like an ISIN. */
    public static boolean looksLikeIsin(String value) {
        return value != null && ISIN_PATTERN.matcher(value.trim().toUpperCase(Locale.ROOT)).matches();
    }
}
