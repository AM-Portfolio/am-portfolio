package com.am.common.amcommondata.service;

import com.am.common.amcommondata.document.portfolio.PortfolioSnapshotDocument;
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

    public void saveSnapshot(String portfolioId, String userId, Double closeValue, Double investment, Double gainLoss, Double gainLossPercentage) {
        LocalDate today = LocalDate.now();

        // 1. Idempotency check: don't save if already saved today
        Optional<PortfolioSnapshotDocument> existing = portfolioSnapshotRepository.findByPortfolioIdAndSnapshotDate(portfolioId, today);
        if (existing.isPresent()) {
            log.info("Snapshot already exists for portfolio {} on date {}. Skipping.", portfolioId, today);
            return;
        }

        // 2. Determine 'open' value (previous day's close)
        Double openValue = closeValue; // Default to close if no previous data
        List<PortfolioSnapshotDocument> previousSnapshots = portfolioSnapshotRepository
                .findByPortfolioIdOrderBySnapshotDateDesc(portfolioId, PageRequest.of(0, 1));
        
        if (!previousSnapshots.isEmpty() && previousSnapshots.get(0).getClose() != null) {
            openValue = previousSnapshots.get(0).getClose();
        }

        // 3. Create and save new snapshot
        PortfolioSnapshotDocument snapshot = PortfolioSnapshotDocument.builder()
                .portfolioId(portfolioId)
                .userId(userId)
                .snapshotDate(today)
                .open(openValue)
                .high(closeValue) // EOD snapshot, high = close
                .low(closeValue)  // EOD snapshot, low = close
                .close(closeValue)
                .totalInvestment(investment)
                .totalGainLoss(gainLoss)
                .totalGainLossPercentage(gainLossPercentage)
                .createdAt(LocalDateTime.now())
                .build();

        portfolioSnapshotRepository.save(snapshot);
        log.info("Successfully saved EOD snapshot for portfolio {} with close value {}", portfolioId, closeValue);
    }

    public List<PortfolioSnapshotModel> getHistory(String portfolioId, String timeFrame) {
        int limit = 5; // Default 1W = 5 trading days
        if ("1M".equalsIgnoreCase(timeFrame)) {
            limit = 22; // 1M = ~22 trading days
        }

        List<PortfolioSnapshotDocument> documents = portfolioSnapshotRepository
                .findByPortfolioIdOrderBySnapshotDateDesc(portfolioId, PageRequest.of(0, limit));

        if (documents.isEmpty()) {
            return Collections.emptyList();
        }

        // Reverse to chronological order (oldest first) for charts
        Collections.reverse(documents);

        return documents.stream()
                .map(this::toModel)
                .collect(Collectors.toList());
    }

    private PortfolioSnapshotModel toModel(PortfolioSnapshotDocument doc) {
        return PortfolioSnapshotModel.builder()
                .portfolioId(doc.getPortfolioId())
                .userId(doc.getUserId())
                .snapshotDate(doc.getSnapshotDate())
                .open(doc.getOpen())
                .high(doc.getHigh())
                .low(doc.getLow())
                .close(doc.getClose())
                .totalInvestment(doc.getTotalInvestment())
                .totalGainLoss(doc.getTotalGainLoss())
                .totalGainLossPercentage(doc.getTotalGainLossPercentage())
                .createdAt(doc.getCreatedAt())
                .build();
    }
}
