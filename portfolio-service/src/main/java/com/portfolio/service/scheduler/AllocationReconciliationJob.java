package com.portfolio.service.scheduler;

import com.am.common.amcommondata.model.PortfolioModelV1;
import com.am.common.amcommondata.model.enums.PortfolioKind;
import com.am.common.amcommondata.document.ledger.AllocationLedgerEntry;
import com.am.common.amcommondata.model.ledger.AllocationLedgerStatus;
import com.am.common.amcommondata.repository.ledger.AllocationLedgerRepository;
import com.am.common.amcommondata.repository.portfolio.PortfolioDocumentRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;

@Component
@Slf4j
@RequiredArgsConstructor
public class AllocationReconciliationJob {

    private final PortfolioDocumentRepository portfolioRepository;
    private final AllocationLedgerRepository ledgerRepository;

    @Scheduled(fixedDelay = 60000)
    public void reconcileOrphanedBaskets() {
        log.info("Starting AllocationReconciliationJob for orphaned baskets");
        
        // Find recent baskets (e.g., created more than 5 mins ago)
        LocalDateTime cutoff = LocalDateTime.now().minusMinutes(5);
        
        // In a real app we might query specifically for baskets > 5 mins old and kind=BASKET.
        // For now, let's find all BASKET portfolios created before cutoff that might be orphaned.
        List<com.am.common.amcommondata.document.portfolio.PortfolioDocument> baskets = portfolioRepository.findAll();
        
        for (var basket : baskets) {
            if (basket.getPortfolioKind() == PortfolioKind.BASKET 
                && basket.getAudit() != null 
                && basket.getAudit().getCreatedAt() != null
                && basket.getAudit().getCreatedAt().isBefore(cutoff)) {
                
                List<AllocationLedgerEntry> pending = ledgerRepository.findByBasketIdAndStatus(basket.getId().toString(), AllocationLedgerStatus.PENDING);
                List<AllocationLedgerEntry> active = ledgerRepository.findByBasketIdAndStatus(basket.getId().toString(), AllocationLedgerStatus.ACTIVE);
                
                if (pending.isEmpty() && active.isEmpty()) {
                    // It's an orphan! Mark it as FAILED or DELETED so it doesn't show up.
                    log.warn("Found orphaned basket document without ledger entries: {}. Marking DELETED.", basket.getId());
                    basket.setPortfolioKind(PortfolioKind.DELETED);
                    portfolioRepository.save(basket);
                }
            }
        }
    }
}
