package com.portfolio.service.scheduler;

import com.am.common.amcommondata.model.PortfolioModelV1;
import com.am.common.amcommondata.model.enums.PortfolioKind;
import com.am.common.amcommondata.document.ledger.AllocationLedgerEntry;
import com.am.common.amcommondata.model.ledger.AllocationLedgerStatus;
import com.am.common.amcommondata.repository.ledger.AllocationLedgerRepository;
import com.am.common.amcommondata.repository.portfolio.PortfolioDocumentRepository;
import com.am.common.amcommondata.service.PortfolioService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

// SAFETY: This job is DISABLED by default. It must be explicitly enabled via config.
// Set portfolio.reconciliation.enabled=true ONLY after the allocation_ledger has been fully
// migrated from legacy allocations data. Without migration, this job will mark ALL existing
// BASKET portfolios as DELETED (since they have no ledger entries).
@ConditionalOnProperty(name = "portfolio.reconciliation.enabled", havingValue = "true", matchIfMissing = false)
@Component
@Slf4j
@RequiredArgsConstructor
public class AllocationReconciliationJob {

    private final PortfolioDocumentRepository portfolioRepository;
    private final AllocationLedgerRepository ledgerRepository;
    private final PortfolioService portfolioService;

    @Scheduled(fixedDelay = 60000)
    public void reconcileOrphanedBaskets() {
        log.info("Starting AllocationReconciliationJob for orphaned and underfunded baskets");
        
        LocalDateTime cutoff = LocalDateTime.now().minusMinutes(5);
        List<com.am.common.amcommondata.document.portfolio.PortfolioDocument> baskets = portfolioRepository.findAll();
        
        for (var basket : baskets) {
            if (basket.getPortfolioKind() == PortfolioKind.BASKET) {
                
                List<AllocationLedgerEntry> pending = ledgerRepository.findByBasketIdAndStatus(basket.getId().toString(), AllocationLedgerStatus.PENDING);
                List<AllocationLedgerEntry> active = ledgerRepository.findByBasketIdAndStatus(basket.getId().toString(), AllocationLedgerStatus.ACTIVE);
                
                // 1. Orphan check
                // SAFETY GUARD: Only consider a basket orphaned if it was created AFTER the
                // allocation_ledger feature was introduced (2026-08-30). Existing baskets
                // created before this date used the old 'allocations' array, not the ledger.
                LocalDate ledgerFeatureDate = LocalDate.of(2026, 8, 30);
                boolean createdAfterLedgerFeature = basket.getAudit() != null
                        && basket.getAudit().getCreatedAt() != null
                        && basket.getAudit().getCreatedAt().toLocalDate().isAfter(ledgerFeatureDate);

                if (createdAfterLedgerFeature && basket.getAudit().getCreatedAt().isBefore(cutoff)) {
                    if (pending.isEmpty() && active.isEmpty()) {
                        log.warn("Found orphaned basket document without ledger entries: {}. Marking DELETED.", basket.getId());
                        basket.setPortfolioKind(PortfolioKind.DELETED);
                        portfolioRepository.save(basket);
                        continue;
                    }
                }
                
                // 2. Underfunded check
                if (!active.isEmpty() && basket.getSourcePortfolioId() != null) {
                    portfolioRepository.findById(basket.getSourcePortfolioId()).ifPresent(sourcePortfolio -> {
                        for (AllocationLedgerEntry entry : active) {
                            double raw = 0.0;
                            if (sourcePortfolio.getEquities() != null) {
                                for (var eq : sourcePortfolio.getEquities()) {
                                    if (entry.getIsin().equals(eq.getIsin())) {
                                        raw = eq.getQuantity() != null ? eq.getQuantity() : 0.0;
                                        break;
                                    }
                                }
                            }
                            
                            Double activeSumObj = ledgerRepository.sumActiveQuantityByBrokerPortfolioIdAndIsin(basket.getSourcePortfolioId(), entry.getIsin());
                            double activeSum = activeSumObj != null ? activeSumObj : 0.0;
                            
                            if (raw < activeSum) {
                                double gap = activeSum - raw;
                                portfolioService.markBasketLineUnderfunded(basket.getId().toString(), entry.getIsin(), gap);
                            }
                        }
                    });
                }
            }
        }
    }
}
