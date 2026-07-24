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
     * Save 1 tick per symbol per minute. Idempotent due to compound unique index. 
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

        if (!docs.isEmpty()) {
            try {
                historyRepo.saveAll(docs);
            } catch (DuplicateKeyException e) {
                // Already have a tick for this minute — safe to ignore
                log.debug("[PriceHistory] Duplicate tick for this minute, ignoring.");
            } catch (Exception e) {
                log.warn("[PriceHistory] Failed to save history tick: {}", e.getMessage());
            }
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
