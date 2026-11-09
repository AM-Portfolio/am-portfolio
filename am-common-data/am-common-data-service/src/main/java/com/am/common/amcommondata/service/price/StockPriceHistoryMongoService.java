package com.am.common.amcommondata.service.price;

import java.time.Instant;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.Collection;
import java.util.List;
import java.util.Date;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;

import com.am.common.amcommondata.document.price.StockPriceDocument;
import com.am.common.amcommondata.document.price.StockPriceHistoryDocument;
import com.am.common.amcommondata.repository.price.StockPriceHistoryMongoRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@RequiredArgsConstructor
@Slf4j
public class StockPriceHistoryMongoService {

    private final StockPriceHistoryMongoRepository historyRepo;
    private static final ZoneId IST = ZoneId.of("Asia/Kolkata");

    /** 
     * Save 1 tick per symbol per minute (wall-clock now). Idempotent due to compound unique index. 
     */
    public void saveAll(List<StockPriceDocument> prices) {
        long minuteEpoch = Instant.now().atZone(IST)
            .truncatedTo(ChronoUnit.MINUTES).toInstant().toEpochMilli();
        
        List<StockPriceHistoryDocument> docs = prices.stream()
            .filter(p -> p.getSymbol() != null && p.getLastPrice() != null && p.getLastPrice() > 0)
            .map(p -> StockPriceHistoryDocument.builder()
                .id(p.getSymbol() + "_" + minuteEpoch)
                .symbol(p.getSymbol())
                .price(p.getLastPrice())
                .timestampMinute(minuteEpoch)
                .createdAt(new Date())
                .build())
            .collect(Collectors.toList());

        persistTicks(docs);
    }

    /**
     * Upsert session ticks with explicit timestamps (candle backfill / warm job).
     * Idempotent via id {@code symbol_timestampMinute} and unique (symbol, timestampMinute).
     *
     * @return number of documents attempted (after filter)
     */
    public int saveSessionTicks(List<StockPriceHistoryDocument> ticks) {
        if (ticks == null || ticks.isEmpty()) {
            return 0;
        }
        Date now = new Date();
        List<StockPriceHistoryDocument> docs = ticks.stream()
            .filter(t -> t != null
                    && t.getSymbol() != null && !t.getSymbol().isBlank()
                    && t.getPrice() != null && t.getPrice() > 0
                    && t.getTimestampMinute() != null && t.getTimestampMinute() > 0)
            .map(t -> {
                String symbol = t.getSymbol().trim();
                long minute = Instant.ofEpochMilli(t.getTimestampMinute())
                        .atZone(IST)
                        .truncatedTo(ChronoUnit.MINUTES)
                        .toInstant()
                        .toEpochMilli();
                return StockPriceHistoryDocument.builder()
                        .id(symbol + "_" + minute)
                        .symbol(symbol)
                        .price(t.getPrice())
                        .timestampMinute(minute)
                        .createdAt(t.getCreatedAt() != null ? t.getCreatedAt() : now)
                        .build();
            })
            .collect(Collectors.toList());
        persistTicks(docs);
        return docs.size();
    }

    private void persistTicks(List<StockPriceHistoryDocument> docs) {
        if (docs.isEmpty()) {
            return;
        }
        try {
            historyRepo.saveAll(docs);
        } catch (DuplicateKeyException e) {
            // Already have a tick for this minute — safe to ignore
            log.debug("[PriceHistory] Duplicate tick for this minute, ignoring.");
        } catch (Exception e) {
            log.warn("[PriceHistory] Failed to save history tick: {}", e.getMessage());
        }
    }

    /** 
     * Returns history grouped by symbol for the given trading day start epoch. 
     */
    public Map<String, List<StockPriceHistoryDocument>> getIntradayHistory(
            Collection<String> symbols, long startEpoch) {
        return historyRepo.findBySymbolInAndTimestampMinuteGreaterThanEqualOrderByTimestampMinuteAsc(
                symbols, startEpoch)
            .stream()
            .collect(Collectors.groupingBy(StockPriceHistoryDocument::getSymbol));
    }
}
