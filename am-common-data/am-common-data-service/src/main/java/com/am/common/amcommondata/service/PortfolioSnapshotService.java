package com.am.common.amcommondata.service;

import com.am.common.amcommondata.document.portfolio.PortfolioSnapshotDocument;
import com.am.common.amcommondata.document.portfolio.PortfolioSnapshotEntry;
import com.am.common.amcommondata.model.HoldingSnapshotItemModel;
import com.am.common.amcommondata.model.PortfolioSnapshotEntryModel;
import com.am.common.amcommondata.model.PortfolioSnapshotModel;
import com.am.common.amcommondata.repository.portfolio.PortfolioSnapshotRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
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

    private static final java.util.Map<String, Integer> TIMEFRAME_LIMITS = java.util.Map.of(
        "1W",  5,
        "1M",  22,
        "3M",  66,
        "6M",  130,
        "1Y",  252,
        "ALL", Integer.MAX_VALUE
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
        int limit = TIMEFRAME_LIMITS.getOrDefault(
            timeFrame != null ? timeFrame.toUpperCase() : "1M", 22
        );

        // Fetch user-level documents
        List<PortfolioSnapshotDocument> documents = portfolioSnapshotRepository
                .findByUserIdOrderBySnapshotDateDesc(userId, PageRequest.of(0, limit));

        if (documents.isEmpty()) {
            return Collections.emptyList();
        }

        // Reverse to chronological order (oldest first) for charts
        Collections.reverse(documents);

        return documents.stream()
                .map(doc -> toModel(doc, portfolioId))
                .collect(Collectors.toList());
    }

    private PortfolioSnapshotModel toModel(PortfolioSnapshotDocument doc, String targetPortfolioId) {
        // Map nested portfolios
        List<PortfolioSnapshotEntryModel> entryModels = Collections.emptyList();
        if (doc.getPortfolios() != null) {
            entryModels = doc.getPortfolios().stream()
                    .filter(p -> targetPortfolioId == null || targetPortfolioId.equals(p.getPortfolioId()))
                    .map(this::toEntryModel)
                    .collect(Collectors.toList());
        }

        double totalOpen = doc.getPortfolios() != null ? doc.getPortfolios().stream().mapToDouble(p -> p.getOpen() != null ? p.getOpen() : 0.0).sum() : doc.getTotalUserWealth();
        double totalHigh = doc.getPortfolios() != null ? doc.getPortfolios().stream().mapToDouble(p -> p.getHigh() != null ? p.getHigh() : 0.0).sum() : doc.getTotalUserWealth();
        double totalLow = doc.getPortfolios() != null ? doc.getPortfolios().stream().mapToDouble(p -> p.getLow() != null ? p.getLow() : 0.0).sum() : doc.getTotalUserWealth();

        // Map root
        return PortfolioSnapshotModel.builder()
                .snapshotId(doc.getSnapshotId())
                .userId(doc.getUserId())
                .snapshotDate(doc.getSnapshotDate())
                .totalUserWealth(doc.getTotalUserWealth())
                .totalUserWealthOpen(totalOpen) 
                .totalUserWealthHigh(totalHigh)
                .totalUserWealthLow(totalLow)
                .totalUserInvestment(doc.getTotalUserInvestment())
                .totalUserGainLoss(doc.getTotalUserGainLoss())
                .totalUserGainLossPercentage(doc.getTotalUserGainLossPercentage())
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
