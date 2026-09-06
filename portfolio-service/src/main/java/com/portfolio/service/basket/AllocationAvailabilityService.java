package com.portfolio.service.basket;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Single-query allocation availability for basket create flows.
 */
@Service
@RequiredArgsConstructor
public class AllocationAvailabilityService {

    private final AllocationLedgerService allocationLedgerService;

    public Map<String, Double> getActiveAllocations(String brokerPortfolioId) {
        return new HashMap<>(allocationLedgerService.getActiveAllocationsMap(brokerPortfolioId));
    }

    public double getInFlightAllocated(List<AllocationLedgerService.AllocationLine> ledgerLines, String isin) {
        if (ledgerLines == null || isin == null) {
            return 0.0;
        }
        return ledgerLines.stream()
                .filter(a -> isin.equals(a.getIsin()))
                .mapToDouble(a -> a.getQuantity() != null ? a.getQuantity() : 0)
                .sum();
    }

    public double getAvailableQuantity(
            Map<String, Double> activeAllocations,
            String isin,
            double rawQty,
            double inFlightAllocated) {
        double dbAllocated = activeAllocations != null ? activeAllocations.getOrDefault(isin, 0.0) : 0.0;
        return Math.max(0.0, rawQty - dbAllocated - inFlightAllocated);
    }

    public double getAvailableAfterAllocation(
            Map<String, Double> activeAllocations,
            String isin,
            double rawQty,
            double additionalAllocation) {
        double dbAllocated = activeAllocations != null ? activeAllocations.getOrDefault(isin, 0.0) : 0.0;
        return Math.max(0.0, rawQty - dbAllocated - additionalAllocation);
    }
}
