package com.am.common.amcommondata.service;

import com.am.common.amcommondata.document.portfolio.PortfolioSnapshotDocument;
import com.am.common.amcommondata.document.portfolio.PortfolioSnapshotEntry;
import com.am.common.amcommondata.model.HoldingSnapshotItemModel;
import com.am.common.amcommondata.model.PortfolioSnapshotEntryModel;
import com.am.common.amcommondata.model.PortfolioSnapshotModel;
import com.am.common.amcommondata.repository.portfolio.PortfolioSnapshotRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class PortfolioSnapshotService {

    private final PortfolioSnapshotRepository portfolioSnapshotRepository;

    private static final java.util.Map<String, java.time.Period> TIMEFRAME_PERIODS = java.util.Map.of(
        "1W",  java.time.Period.ofWeeks(1),
        "1M",  java.time.Period.ofMonths(1),
        "3M",  java.time.Period.ofMonths(3),
        "6M",  java.time.Period.ofMonths(6),
        "1Y",  java.time.Period.ofYears(1)
    );

    public void saveUserSnapshot(String userId, Double totalUserWealth, Double totalUserInvestment, Double totalUserGainLoss, Double totalUserGainLossPercentage, List<PortfolioSnapshotEntry> entries) {
        LocalDate today = LocalDate.now();

        // 1. Idempotency check: don't save if already saved today
        Optional<PortfolioSnapshotDocument> existing = portfolioSnapshotRepository.findByUserIdAndSnapshotDate(userId, today);
        if (existing.isPresent()) {
            log.info("Snapshot already exists for user {} on date {}. Skipping.", userId, today);
            return;
        }

        // 2. Pre-generate a UUID so snapshotId == _id from the start (single save, no double-write)
        String snapshotId = java.util.UUID.randomUUID().toString();

        // 3. Create and save new snapshot — holdings are now nested inside each PortfolioSnapshotEntry
        PortfolioSnapshotDocument snapshot = PortfolioSnapshotDocument.builder()
                .id(snapshotId)          // sets MongoDB _id
                .snapshotId(snapshotId)  // duplicate human-readable field
                .userId(userId)
                .snapshotDate(today)
                .totalUserWealth(totalUserWealth)
                .totalUserInvestment(totalUserInvestment)
                .totalUserGainLoss(totalUserGainLoss)
                .totalUserGainLossPercentage(totalUserGainLossPercentage)
                .portfolios(entries)
                .createdAt(LocalDateTime.now())
                .build();

        portfolioSnapshotRepository.save(snapshot);
        log.info("Successfully saved User-Centric EOD snapshot for user {} snapshotId={} totalWealth={}", userId, snapshotId, totalUserWealth);
    }

    public List<PortfolioSnapshotModel> getHistory(String userId, String portfolioId, String timeFrame) {
        String frame = timeFrame != null ? timeFrame.toUpperCase() : "1M";
        LocalDate today = LocalDate.now();
        List<PortfolioSnapshotDocument> documents;

        if ("ALL".equals(frame)) {
            // No date filter → fetch entire history, already sorted ASC by DB
            documents = portfolioSnapshotRepository.findByUserIdOrderBySnapshotDateAsc(userId);
        } else {
            // Real date math: 1M = exactly 1 calendar month ago
            java.time.Period period = TIMEFRAME_PERIODS.getOrDefault(frame, java.time.Period.ofMonths(1));
            LocalDate fromDate = today.minus(period);
            documents = portfolioSnapshotRepository
                .findByUserIdAndSnapshotDateBetweenOrderBySnapshotDateAsc(userId, fromDate, today.plusDays(1));
        }

        if (documents == null || documents.isEmpty()) {
            return Collections.emptyList();
        }

        return documents.stream()
                .map(doc -> toModel(doc, portfolioId))
                .collect(Collectors.toList());
    }

    private PortfolioSnapshotModel toModel(PortfolioSnapshotDocument doc, String targetPortfolioId) {
        double wealth = 0.0, investment = 0.0, open = 0.0, high = 0.0, low = 0.0;
        List<PortfolioSnapshotEntryModel> entryModels = new java.util.ArrayList<>();

        if (doc.getPortfolios() != null) {
            for (PortfolioSnapshotEntry p : doc.getPortfolios()) {
                boolean matches = targetPortfolioId == null || targetPortfolioId.equals(p.getPortfolioId());
                if (!matches) continue;

                double close = p.getClose() != null ? p.getClose() : 0.0;
                open       += p.getOpen()   != null ? p.getOpen()   : close;
                high       += p.getHigh()   != null ? p.getHigh()   : close;
                low        += p.getLow()    != null ? p.getLow()    : close;
                wealth     += close;
                investment += p.getTotalInvestment() != null ? p.getTotalInvestment() : 0.0;
                entryModels.add(toEntryModel(p));
            }
        } else {
            // Null-safe fallback for documents without nested portfolios
            double fallback = doc.getTotalUserWealth() != null ? doc.getTotalUserWealth() : 0.0;
            wealth = fallback; open = fallback; high = fallback; low = fallback;
            investment = doc.getTotalUserInvestment() != null ? doc.getTotalUserInvestment() : 0.0;
        }

        double gainLoss    = wealth - investment;
        double gainLossPct = investment > 0 ? (gainLoss / investment) * 100.0 : 0.0;

        return PortfolioSnapshotModel.builder()
                .snapshotId(doc.getSnapshotId())
                .userId(doc.getUserId())
                .snapshotDate(doc.getSnapshotDate())
                .totalUserWealth(wealth)
                .totalUserWealthOpen(open)
                .totalUserWealthHigh(high)
                .totalUserWealthLow(low)
                .totalUserInvestment(investment)
                .totalUserGainLoss(gainLoss)
                .totalUserGainLossPercentage(gainLossPct)
                .portfolios(entryModels)
                .createdAt(doc.getCreatedAt())
                .build();
    }

    private PortfolioSnapshotEntryModel toEntryModel(PortfolioSnapshotEntry entry) {
        List<HoldingSnapshotItemModel> holdingModels = Collections.emptyList();
        if (entry.getHoldings() != null) {
            holdingModels = entry.getHoldings().stream()
                    .map(h -> HoldingSnapshotItemModel.builder()
                            .symbol(h.getSymbol())
                            .isin(h.getIsin())
                            .quantity(h.getQuantity())
                            .avgBuyPrice(h.getAvgBuyPrice())
                            .build())
                    .collect(Collectors.toList());
        }

        return PortfolioSnapshotEntryModel.builder()
                .portfolioId(entry.getPortfolioId())
                .portfolioName(entry.getPortfolioName())
                .brokerType(entry.getBrokerType())
                .open(entry.getOpen())
                .high(entry.getHigh())
                .low(entry.getLow())
                .close(entry.getClose())
                .totalInvestment(entry.getTotalInvestment())
                .totalGainLoss(entry.getTotalGainLoss())
                .totalGainLossPercentage(entry.getTotalGainLossPercentage())
                .holdings(holdingModels)
                .build();
    }
}
