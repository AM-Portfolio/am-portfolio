package com.am.common.amcommondata.service;

import com.am.common.amcommondata.document.portfolio.PortfolioDocument;
import com.am.common.amcommondata.mapper.PortfolioMapper;
import com.am.common.amcommondata.model.PortfolioModelV1;
import com.am.common.amcommondata.repository.portfolio.PortfolioDocumentRepository;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
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
                // Update existing equities
                if (portfolioModel.getEquityModels() != null) {
                    existing.setEquities(portfolioMapper.toDocument(portfolioModel).getEquities());
                }
                existing.setTotalValue(portfolioModel.getTotalValue());
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
    
    @Override
    public List<String> getAllUserIds() {
        return portfolioDocumentRepository.findAllDistinctOwners();
    }
}
