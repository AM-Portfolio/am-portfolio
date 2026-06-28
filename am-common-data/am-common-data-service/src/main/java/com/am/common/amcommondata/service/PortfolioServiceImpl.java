package com.am.common.amcommondata.service;

import com.am.common.amcommondata.document.portfolio.PortfolioDocument;
import com.am.common.amcommondata.mapper.PortfolioMapper;
import com.am.common.amcommondata.model.PortfolioModelV1;
import com.am.common.amcommondata.repository.portfolio.PortfolioDocumentRepository;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDate;
import java.util.List;
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
        if (portfolioModel.getName() != null) { // name contains portfolioId from mapper
            String portfolioId = portfolioModel.getName();
            PortfolioDocument existing = portfolioDocumentRepository.findById(portfolioId).orElse(null);
            
            if (existing != null) {
                // Smart holding calculation logic
                String tradeAction = portfolioModel.getLastTradeAction();
                List<com.am.common.amcommondata.document.asset.equity.EquityDocument> incomingEquities = portfolioMapper.toDocument(portfolioModel).getEquities();
                
                List<com.am.common.amcommondata.document.asset.equity.EquityDocument> existingEquities = existing.getEquities();
                if (existingEquities == null) {
                    existingEquities = new java.util.ArrayList<>();
                }

                if (incomingEquities != null) {
                    for (com.am.common.amcommondata.document.asset.equity.EquityDocument incoming : incomingEquities) {
                        String isin = incoming.getIsin();
                        
                        java.util.Optional<com.am.common.amcommondata.document.asset.equity.EquityDocument> matchOpt = existingEquities.stream()
                            .filter(e -> e.getIsin() != null && e.getIsin().equals(isin))
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
                        } else if ("SELL".equalsIgnoreCase(tradeAction)) {
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
                            } else {
                                // SELL received for ISIN not in holdings - skipping silently or log warning.
                            }
                        }
                    }
                }

                // Recalculate total portfolio value
                double totalValue = existingEquities.stream()
                    .mapToDouble(e -> {
                        double qty = e.getQuantity() != null ? e.getQuantity() : 0.0;
                        double price = e.getCurrentPrice() != null ? e.getCurrentPrice() : (e.getAvgBuyingPrice() != null ? e.getAvgBuyingPrice() : 0.0);
                        return qty * price;
                    })
                    .sum();

                existing.setEquities(existingEquities);
                existing.setTotalValue(totalValue);
                
                return portfolioMapper.toModel(portfolioDocumentRepository.save(existing));
            }
        }
        return null;
    }

    @Transactional
    public PortfolioModelV1 createPortfolio(PortfolioModelV1 portfolioModel) {
        if (portfolioModel.getBrokerType() != null && portfolioModel.getOwner() != null) {
            String owner = portfolioModel.getOwner();
            com.am.common.amcommondata.model.enums.BrokerType brokerType = portfolioModel.getBrokerType();
            String baseName = brokerType.getCode();
            
            List<PortfolioDocument> existingPortfolios = portfolioDocumentRepository.findByOwner(owner)
                    .stream()
                    .filter(p -> p.getBrokerType() == brokerType)
                    .collect(Collectors.toList());
            
            int count = existingPortfolios.size();
            
            if (count >= 5) {
                // Find oldest portfolio
                PortfolioDocument oldest = existingPortfolios.stream()
                    .min(java.util.Comparator.comparing(p -> p.getAudit() != null && p.getAudit().getCreatedAt() != null ? 
                        p.getAudit().getCreatedAt() : java.time.LocalDateTime.MAX))
                    .orElse(existingPortfolios.get(0));
                
                portfolioDocumentRepository.delete(oldest);
                existingPortfolios.remove(oldest);
                count--;
            }
            
            if (count == 0) {
                portfolioModel.setName(baseName);
            } else if (count == 1) {
                PortfolioDocument first = existingPortfolios.get(0);
                if (first.getName() == null || first.getName().equalsIgnoreCase(baseName)) {
                    first.setName(baseName + "-V1");
                    portfolioDocumentRepository.save(first);
                }
                portfolioModel.setName(baseName + "-V2");
            } else {
                // Find max version to prevent V3 colliding with V3 after a delete
                int maxVersion = 0;
                for (PortfolioDocument p : existingPortfolios) {
                    if (p.getName() != null && p.getName().startsWith(baseName + "-V")) {
                        try {
                            int v = Integer.parseInt(p.getName().substring((baseName + "-V").length()));
                            if (v > maxVersion) maxVersion = v;
                        } catch (Exception e) {}
                    }
                }
                if (maxVersion == 0) maxVersion = count;
                portfolioModel.setName(baseName + "-V" + (maxVersion + 1));
            }
        }
        
        PortfolioDocument document = portfolioMapper.toDocument(portfolioModel);
        return portfolioMapper.toModel(portfolioDocumentRepository.save(document));
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

        if (existingDocs != null && !existingDocs.isEmpty()) {
            // Take the first one (most recent usually if sorted, or just the first found)
            PortfolioDocument doc = existingDocs.get(0);
            
            // Optional: clean up any legacy duplicates (V1, V2, etc.) to ensure 1-per-broker strictly
            if (existingDocs.size() > 1) {
                for (int i = 1; i < existingDocs.size(); i++) {
                    portfolioDocumentRepository.delete(existingDocs.get(i));
                }
            }

            PortfolioDocument incoming = portfolioMapper.toDocument(portfolioModel);
            doc.setEquities(incoming.getEquities());
            if (portfolioModel.getTotalValue() != null) {
                doc.setTotalValue(portfolioModel.getTotalValue());
            }
            if (doc.getAudit() != null) {
                doc.getAudit().setUpdatedAt(java.time.LocalDateTime.now());
            }
            return portfolioMapper.toModel(portfolioDocumentRepository.save(doc));
        } else {
            portfolioModel.setName(brokerType.getCode());
            PortfolioDocument document = portfolioMapper.toDocument(portfolioModel);
            return portfolioMapper.toModel(portfolioDocumentRepository.save(document));
        }
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
}
