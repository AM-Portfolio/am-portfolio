package com.portfolio.service.basket;

import com.am.common.amcommondata.document.ledger.AllocationLedgerEntry;
import com.am.common.amcommondata.model.ledger.AllocationLedgerEventType;
import com.am.common.amcommondata.model.ledger.AllocationLedgerStatus;
import com.am.common.amcommondata.repository.ledger.AllocationLedgerRepository;
import lombok.Builder;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
@Slf4j
@RequiredArgsConstructor
public class AllocationLedgerService {

    private final AllocationLedgerRepository ledgerRepository;

    public void reserveAllocations(String basketId, String brokerPortfolioId, List<AllocationLine> lines) {
        List<AllocationLedgerEntry> pendingEntries = new ArrayList<>();
        
        for (AllocationLine line : lines) {
            AllocationLedgerEntry entry = AllocationLedgerEntry.builder()
                    .id(UUID.randomUUID().toString())
                    .basketId(basketId)
                    .brokerPortfolioId(brokerPortfolioId)
                    .isin(line.getIsin())
                    .symbol(line.getSymbol())
                    .quantity(line.getQuantity())
                    .status(AllocationLedgerStatus.PENDING)
                    .eventType(AllocationLedgerEventType.CREATED)
                    .createdAt(LocalDateTime.now())
                    .build();
            pendingEntries.add(entry);
        }
        
        // 1. Write as PENDING
        List<AllocationLedgerEntry> saved = ledgerRepository.saveAll(pendingEntries);
        
        // 2. Immediately flip to ACTIVE
        saved.forEach(e -> e.setStatus(AllocationLedgerStatus.ACTIVE));
        ledgerRepository.saveAll(saved);
        
        log.info("Reserved {} allocations for basketId: {}", saved.size(), basketId);
    }

    public void releaseAllocations(String basketId, String releasedBy, String reason) {
        List<AllocationLedgerEntry> activeEntries = ledgerRepository.findByBasketIdAndStatus(basketId, AllocationLedgerStatus.ACTIVE);
        
        List<AllocationLedgerEntry> releaseEntries = new ArrayList<>();
        for (AllocationLedgerEntry active : activeEntries) {
            AllocationLedgerEntry releaseEntry = AllocationLedgerEntry.builder()
                    .id(UUID.randomUUID().toString())
                    .basketId(basketId)
                    .brokerPortfolioId(active.getBrokerPortfolioId())
                    .isin(active.getIsin())
                    .symbol(active.getSymbol())
                    .quantity(active.getQuantity())
                    .status(AllocationLedgerStatus.RELEASED)
                    .eventType(AllocationLedgerEventType.RELEASED)
                    .createdAt(active.getCreatedAt())
                    .releasedAt(LocalDateTime.now())
                    .releasedBy(releasedBy)
                    .reason(reason)
                    .build();
            releaseEntries.add(releaseEntry);
        }
        
        ledgerRepository.saveAll(releaseEntries);
        log.info("Released {} allocations for basketId: {}", releaseEntries.size(), basketId);
        
        // Update old active entries to RELEASED status (to avoid counting them as ACTIVE)
        activeEntries.forEach(e -> {
            e.setStatus(AllocationLedgerStatus.RELEASED);
            e.setReleasedAt(LocalDateTime.now());
            e.setReleasedBy(releasedBy);
            e.setReason(reason);
        });
        ledgerRepository.saveAll(activeEntries);
    }

    public Double sumActiveQuantityByBrokerPortfolioIdAndIsin(String brokerPortfolioId, String isin) {
        Double sum = ledgerRepository.sumActiveQuantityByBrokerPortfolioIdAndIsin(brokerPortfolioId, isin);
        return sum != null ? sum : 0.0;
    }

    public java.util.Map<String, Double> getActiveAllocationsMap(String brokerPortfolioId) {
        List<com.am.common.amcommondata.document.ledger.AllocationLedgerSumResult> results = 
            ledgerRepository.sumActiveQuantitiesByBrokerPortfolioId(brokerPortfolioId);
            
        java.util.Map<String, Double> map = new java.util.HashMap<>();
        if (results != null) {
            for (var result : results) {
                if (result.getId() != null && result.getTotalQuantity() != null) {
                    map.put(result.getId(), result.getTotalQuantity());
                }
            }
        }
        return map;
    }

    public List<AllocationLedgerEntry> getActiveAllocations(String basketId) {
        return ledgerRepository.findByBasketIdAndStatus(basketId, AllocationLedgerStatus.ACTIVE);
    }

    @Data
    @Builder
    public static class AllocationLine {
        private String isin;
        private String symbol;
        private Double quantity;
    }
}
