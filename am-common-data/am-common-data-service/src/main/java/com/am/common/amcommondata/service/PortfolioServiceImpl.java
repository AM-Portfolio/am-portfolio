package com.am.common.amcommondata.service;

import com.am.common.amcommondata.document.portfolio.HoldingAllocationDocument;
import com.am.common.amcommondata.document.portfolio.PortfolioDocument;
import com.am.common.amcommondata.mapper.PortfolioMapper;
import com.am.common.amcommondata.model.HoldingAllocation;
import com.am.common.amcommondata.model.PortfolioModelV1;
import com.am.common.amcommondata.model.enums.PortfolioKind;
import com.am.common.amcommondata.repository.portfolio.PortfolioDocumentRepository;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

import lombok.extern.slf4j.Slf4j;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
@Slf4j
public class PortfolioServiceImpl implements PortfolioService {
    private final PortfolioDocumentRepository portfolioDocumentRepository;
    private final PortfolioMapper portfolioMapper;

    @Override
    public List<PortfolioModelV1> getPortfoliosByUserId(String userId) {
        return portfolioDocumentRepository.findByOwner(userId).stream()
                .map(portfolioMapper::toModel)
                .collect(Collectors.toList());
    }

    @Override
    public PortfolioModelV1 getPortfolioById(UUID id) {
        return portfolioDocumentRepository.findById(id.toString())
        .map(portfolioMapper::toModel)
        .orElse(null);
    }

    @Transactional
    public PortfolioModelV1 updateTradePortfolio(PortfolioModelV1 portfolioModel) {
        String owner = portfolioModel.getOwner();
        com.am.common.amcommondata.model.enums.BrokerType brokerType = portfolioModel.getBrokerType();

        if (owner == null) return null;

        PortfolioDocument doc = null;
        if (portfolioModel.getId() != null) {
            doc = portfolioDocumentRepository.findById(portfolioModel.getId().toString()).orElse(null);
        }

        // Removed fallback to findByOwnerAndBrokerType to support multiple portfolios of the same broker type.

        if (doc != null) {
            if (PortfolioKind.isBasket(doc.getPortfolioKind())) {
                log.warn("Refusing trade update on BASKET portfolio id={}", doc.getId());
                return portfolioMapper.toModel(doc);
            }
            // Update name if the incoming model has a valid name that isn't just the ID
            if (portfolioModel.getName() != null && !portfolioModel.getName().equals(doc.getId())) {
                doc.setName(portfolioModel.getName());
            }
        } else {
            // Create new portfolio
            doc = new PortfolioDocument();
            if (portfolioModel.getId() != null) {
                doc.setId(portfolioModel.getId().toString());
            }
            doc.setOwner(owner);
            doc.setBrokerType(brokerType);
            doc.setPortfolioKind(PortfolioKind.BROKER);
            doc.setName(portfolioModel.getName() != null && !portfolioModel.getName().equals(portfolioModel.getId() != null ? portfolioModel.getId().toString() : "") ? portfolioModel.getName() : (brokerType != null ? brokerType.getCode() : "Other"));
            doc.setStatus(com.am.common.amcommondata.model.enums.DocumentStatus.ACTIVE);
            com.am.common.amcommondata.document.common.AuditMetadata audit = new com.am.common.amcommondata.document.common.AuditMetadata();
            audit.setCreatedBy(owner);
            audit.setCreatedAt(java.time.LocalDateTime.now());
            audit.setUpdatedAt(java.time.LocalDateTime.now());
            doc.setAudit(audit);
            doc.setEquities(new java.util.ArrayList<>());
        }

        // Apply trade delta
        applyTradeEquityDelta(doc, portfolioModel);

        // Update audit timestamp
        if (doc.getAudit() != null) {
            doc.getAudit().setUpdatedAt(java.time.LocalDateTime.now());
        }

        return portfolioMapper.toModel(portfolioDocumentRepository.save(doc));
    }

    private void applyTradeEquityDelta(PortfolioDocument existing, PortfolioModelV1 portfolioModel) {
        String tradeAction = portfolioModel.getLastTradeAction();
        List<com.am.common.amcommondata.document.asset.equity.EquityDocument> incomingEquities = portfolioMapper.toDocument(portfolioModel).getEquities();
        
        List<com.am.common.amcommondata.document.asset.equity.EquityDocument> existingEquities = existing.getEquities();
        if (existingEquities == null) {
            existingEquities = new java.util.ArrayList<>();
        }

        if ("REPLACE_ALL".equalsIgnoreCase(tradeAction)) {
            existingEquities = incomingEquities != null ? new java.util.ArrayList<>(incomingEquities) : new java.util.ArrayList<>();
        } else if (incomingEquities != null && !incomingEquities.isEmpty()) {
            for (com.am.common.amcommondata.document.asset.equity.EquityDocument incoming : incomingEquities) {
                String isin = incoming.getIsin();
                
                java.util.Optional<com.am.common.amcommondata.document.asset.equity.EquityDocument> matchOpt = existingEquities.stream()
                    .filter(e -> (e.getIsin() != null && isin != null && e.getIsin().equals(isin)) 
                            || (e.getSymbol() != null && incoming.getSymbol() != null && e.getSymbol().equals(incoming.getSymbol())))
                    .findFirst();

                if ("BUY".equalsIgnoreCase(tradeAction)) {
                    if (matchOpt.isPresent()) {
                        com.am.common.amcommondata.document.asset.equity.EquityDocument match = matchOpt.get();
                        double existingQty = match.getQuantity() != null ? match.getQuantity() : 0.0;
                        double incomingQty = incoming.getQuantity() != null ? incoming.getQuantity() : 0.0;
                        double existingAvg = match.getAvgBuyingPrice() != null ? match.getAvgBuyingPrice() : 0.0;
                        double incomingAvg = incoming.getAvgBuyingPrice() != null ? incoming.getAvgBuyingPrice() : 0.0;
                        
                        double newQty = existingQty + incomingQty;
                        double newAvg = newQty > 0 ? ((existingQty * existingAvg) + (incomingQty * incomingAvg)) / newQty : 0.0;
                        
                        match.setQuantity(newQty);
                        match.setAvgBuyingPrice(newAvg);
                        if (incoming.getCurrentPrice() != null) {
                            match.setCurrentPrice(incoming.getCurrentPrice());
                        }
                    } else {
                        existingEquities.add(incoming);
                    }
                } else if ("SELL".equalsIgnoreCase(tradeAction) || "DELETE".equalsIgnoreCase(tradeAction)) {
                    if (matchOpt.isPresent()) {
                        com.am.common.amcommondata.document.asset.equity.EquityDocument match = matchOpt.get();
                        double existingQty = match.getQuantity() != null ? match.getQuantity() : 0.0;
                        double incomingQty = incoming.getQuantity() != null ? incoming.getQuantity() : 0.0;
                        double newQty = existingQty - incomingQty;
                        
                        if (newQty <= 0) {
                            existingEquities.remove(match);
                        } else {
                            match.setQuantity(newQty);
                            if (incoming.getCurrentPrice() != null) {
                                match.setCurrentPrice(incoming.getCurrentPrice());
                            }
                        }
                    }
                } else if ("UPDATE".equalsIgnoreCase(tradeAction)) {
                    if (matchOpt.isPresent()) {
                        com.am.common.amcommondata.document.asset.equity.EquityDocument match = matchOpt.get();
                        match.setQuantity(incoming.getQuantity());
                        match.setAvgBuyingPrice(incoming.getAvgBuyingPrice());
                        if (incoming.getCurrentPrice() != null) {
                            match.setCurrentPrice(incoming.getCurrentPrice());
                        }
                        if (incoming.getInvestmentValue() != null) {
                            match.setInvestmentValue(incoming.getInvestmentValue());
                        }
                    } else {
                        existingEquities.add(incoming);
                    }
                }
            }
        }

        double totalValue = existingEquities.stream()
            .mapToDouble(e -> {
                double qty = e.getQuantity() != null ? e.getQuantity() : 0.0;
                double price = e.getCurrentPrice() != null ? e.getCurrentPrice() : (e.getAvgBuyingPrice() != null ? e.getAvgBuyingPrice() : 0.0);
                return qty * price;
            })
            .sum();

        existing.setEquities(existingEquities);
        existing.setTotalValue(totalValue);
    }

    @Transactional
    public PortfolioModelV1 createPortfolio(PortfolioModelV1 portfolioModel) {
        if (PortfolioKind.isBasket(portfolioModel.getPortfolioKind())) {
            return createBasketPortfolio(portfolioModel);
        }
        // One perfect BROKER portfolio per owner+broker — no V1/V2, no max-5 delete.
        if (portfolioModel.getBrokerType() != null && portfolioModel.getOwner() != null) {
            portfolioModel.setPortfolioKind(PortfolioKind.BROKER);
            if (portfolioModel.getName() == null || portfolioModel.getName().isBlank()) {
                portfolioModel.setName(portfolioModel.getBrokerType().getCode());
            }
            return upsertDocumentPortfolio(portfolioModel);
        }
        if (portfolioModel.getPortfolioKind() == null) {
            portfolioModel.setPortfolioKind(PortfolioKind.BROKER);
        }
        PortfolioDocument document = portfolioMapper.toDocument(portfolioModel);
        return portfolioMapper.toModel(portfolioDocumentRepository.save(document));
    }

    @Override
    @Transactional
    public PortfolioModelV1 createBasketPortfolio(PortfolioModelV1 portfolioModel) {
        if (portfolioModel.getName() == null || portfolioModel.getName().isBlank()) {
            throw new IllegalArgumentException("Basket portfolio name is required");
        }
        portfolioModel.setPortfolioKind(PortfolioKind.BASKET);
        if (portfolioModel.getCreatedFromBasketAt() == null) {
            portfolioModel.setCreatedFromBasketAt(LocalDateTime.now());
        }
        // Resolve name collisions: "Nifty IT · Zerodha", "Nifty IT · Zerodha · 2", ...
        String baseName = portfolioModel.getName().trim();
        String owner = portfolioModel.getOwner();
        if (owner != null) {
            List<String> existingNames = portfolioDocumentRepository.findByOwner(owner).stream()
                    .map(PortfolioDocument::getName)
                    .filter(n -> n != null)
                    .collect(Collectors.toList());
            portfolioModel.setName(uniqueBasketName(baseName, existingNames));
        }
        PortfolioDocument document = portfolioMapper.toDocument(portfolioModel);
        document.setPortfolioKind(PortfolioKind.BASKET);
        return portfolioMapper.toModel(portfolioDocumentRepository.save(document));
    }

    static String uniqueBasketName(String baseName, List<String> existingNames) {
        if (existingNames.stream().noneMatch(n -> n.equalsIgnoreCase(baseName))) {
            return baseName;
        }
        int suffix = 2;
        while (true) {
            String candidate = baseName + " · " + suffix;
            final String check = candidate;
            if (existingNames.stream().noneMatch(n -> n.equalsIgnoreCase(check))) {
                return candidate;
            }
            suffix++;
        }
    }
    
    @Transactional
    public PortfolioModelV1 upsertDocumentPortfolio(PortfolioModelV1 portfolioModel) {
        if (portfolioModel.getOwner() == null || portfolioModel.getBrokerType() == null) {
            return null;
        }

        String owner = portfolioModel.getOwner();
        com.am.common.amcommondata.model.enums.BrokerType brokerType = portfolioModel.getBrokerType();

        java.util.List<PortfolioDocument> existingDocs =
            portfolioDocumentRepository.findByOwnerAndBrokerType(owner, brokerType);

        // Only BROKER (or legacy null) docs participate in Kafka upsert. Never touch BASKET.
        List<PortfolioDocument> brokerDocs = existingDocs == null ? List.of() : existingDocs.stream()
                .filter(d -> PortfolioKind.isBroker(d.getPortfolioKind()))
                .collect(Collectors.toList());

        if (!brokerDocs.isEmpty()) {
            PortfolioDocument doc = pickCanonicalBroker(brokerDocs);
            // Delete other BROKER duplicates only — never baskets
            for (PortfolioDocument extra : brokerDocs) {
                if (!extra.getId().equals(doc.getId())) {
                    log.info("Cleaning duplicate BROKER portfolio id={} name={} for owner={}",
                            extra.getId(), extra.getName(), owner);
                    portfolioDocumentRepository.delete(extra);
                }
            }

            PortfolioDocument incoming = portfolioMapper.toDocument(portfolioModel);
            doc.setEquities(incoming.getEquities());
            if (portfolioModel.getTotalValue() != null) {
                doc.setTotalValue(portfolioModel.getTotalValue());
            }
            doc.setPortfolioKind(PortfolioKind.BROKER);
            // Keep stable broker name (no Zerodha-V*)
            if (doc.getName() == null || doc.getName().isBlank()
                    || doc.getName().matches("(?i)" + java.util.regex.Pattern.quote(brokerType.getCode()) + "-V\\d+")) {
                doc.setName(brokerType.getCode());
            }
            clampAllocations(doc);
            if (doc.getAudit() != null) {
                doc.getAudit().setUpdatedAt(LocalDateTime.now());
            }
            return portfolioMapper.toModel(portfolioDocumentRepository.save(doc));
        } else {
            portfolioModel.setName(brokerType.getCode());
            portfolioModel.setPortfolioKind(PortfolioKind.BROKER);
            PortfolioDocument document = portfolioMapper.toDocument(portfolioModel);
            document.setPortfolioKind(PortfolioKind.BROKER);
            return portfolioMapper.toModel(portfolioDocumentRepository.save(document));
        }
    }

    private PortfolioDocument pickCanonicalBroker(List<PortfolioDocument> brokerDocs) {
        return brokerDocs.stream()
                .max(Comparator
                        .comparing((PortfolioDocument p) -> p.getTotalValue() != null ? p.getTotalValue() : 0.0)
                        .thenComparing(p -> p.getAudit() != null && p.getAudit().getUpdatedAt() != null
                                ? p.getAudit().getUpdatedAt()
                                : LocalDateTime.MIN))
                .orElse(brokerDocs.get(0));
    }

    /**
     * Clamp allocation quantities to current broker holding quantities after Kafka refresh.
     */
    void clampAllocations(PortfolioDocument doc) {
        if (doc.getAllocations() == null || doc.getAllocations().isEmpty()) {
            return;
        }
        Map<String, Double> qtyByIsin = new HashMap<>();
        if (doc.getEquities() != null) {
            for (var e : doc.getEquities()) {
                if (e.getIsin() != null) {
                    qtyByIsin.merge(e.getIsin(), e.getQuantity() != null ? e.getQuantity() : 0.0, Double::sum);
                }
            }
        }
        List<HoldingAllocationDocument> clamped = new ArrayList<>();
        for (HoldingAllocationDocument alloc : doc.getAllocations()) {
            if (alloc == null || alloc.getQuantity() == null || alloc.getQuantity() <= 0) {
                continue;
            }
            double brokerQty = alloc.getIsin() != null ? qtyByIsin.getOrDefault(alloc.getIsin(), 0.0) : 0.0;
            // Sum of allocations for same ISIN must not exceed broker qty — clamp this row against remaining
            double already = clamped.stream()
                    .filter(a -> alloc.getIsin() != null && alloc.getIsin().equals(a.getIsin()))
                    .mapToDouble(a -> a.getQuantity() != null ? a.getQuantity() : 0.0)
                    .sum();
            double maxForThis = Math.max(0.0, brokerQty - already);
            double newQty = Math.min(alloc.getQuantity(), maxForThis);
            if (newQty <= 1e-9) {
                log.warn("Clamped allocation to 0 for isin={} basketId={} (broker sold down)",
                        alloc.getIsin(), alloc.getBasketPortfolioId());
                continue;
            }
            if (newQty < alloc.getQuantity()) {
                log.warn("Clamped allocation isin={} basketId={} from {} to {}",
                        alloc.getIsin(), alloc.getBasketPortfolioId(), alloc.getQuantity(), newQty);
                alloc.setQuantity(newQty);
            }
            clamped.add(alloc);
        }
        doc.setAllocations(clamped);
    }

    @Override
    @Transactional
    public PortfolioModelV1 savePortfolioDocument(PortfolioModelV1 portfolioModel) {
        PortfolioDocument document = portfolioMapper.toDocument(portfolioModel);
        // Preserve id when updating
        if (portfolioModel.getId() != null) {
            document.setId(portfolioModel.getId().toString());
            portfolioDocumentRepository.findById(portfolioModel.getId().toString()).ifPresent(existing -> {
                if (document.getAudit() == null) {
                    document.setAudit(existing.getAudit());
                }
                if (document.getPortfolioKind() == null) {
                    document.setPortfolioKind(existing.getPortfolioKind());
                }
            });
        }
        return portfolioMapper.toModel(portfolioDocumentRepository.save(document));
    }

    @Override
    public double getAllocatedQuantity(PortfolioModelV1 brokerPortfolio, String isin) {
        if (brokerPortfolio == null || brokerPortfolio.getAllocations() == null || isin == null) {
            return 0.0;
        }
        return brokerPortfolio.getAllocations().stream()
                .filter(a -> isin.equals(a.getIsin()))
                .mapToDouble(a -> a.getQuantity() != null ? a.getQuantity() : 0.0)
                .sum();
    }

    @Override
    public double getAvailableQuantity(PortfolioModelV1 brokerPortfolio, String isin, Double rawQuantity) {
        double raw = rawQuantity != null ? rawQuantity : 0.0;
        return Math.max(0.0, raw - getAllocatedQuantity(brokerPortfolio, isin));
    }
    
    @Override
    public List<String> getAllUserIds() {
        return portfolioDocumentRepository.findAllDistinctOwners();
    }

    @Override
    public List<String> getActiveUserIds(LocalDate cutoffDate) {
        return portfolioDocumentRepository.findActiveOwnersSince(cutoffDate);
    }

    @Override
    @Transactional
    public void updateLastLoginDate(String userId, LocalDate loginDate) {
        List<PortfolioDocument> portfolios = portfolioDocumentRepository.findByOwner(userId);
        if (portfolios != null && !portfolios.isEmpty()) {
            portfolios.forEach(p -> p.setLastLoginDate(loginDate));
            portfolioDocumentRepository.saveAll(portfolios);
            log.info("Updated lastLoginDate={} for {} portfolios of user={}", loginDate, portfolios.size(), userId);
        }
    }

    @Override
    @Transactional
    public void deletePortfolioByIdAndOwner(String id, String owner) {
        if (id == null || owner == null) {
            return;
        }
        
        List<PortfolioDocument> portfolios = portfolioDocumentRepository.findByOwner(owner);
        for (PortfolioDocument portfolio : portfolios) {
            if (id.equals(portfolio.getName())) {
                portfolioDocumentRepository.delete(portfolio);
                log.info("Deleted portfolio with name: {} and owner: {}", id, owner);
                return;
            }
        }
        log.warn("Portfolio not found for deletion with name: {} and owner: {}", id, owner);
    }
}
