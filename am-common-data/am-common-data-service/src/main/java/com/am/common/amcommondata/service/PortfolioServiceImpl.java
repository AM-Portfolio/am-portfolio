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
                throw new IllegalStateException("MAX_VERSIONS_EXCEEDED");
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
                portfolioModel.setName(baseName + "-V" + (count + 1));
            }
        }
        
        PortfolioDocument document = portfolioMapper.toDocument(portfolioModel);
        return portfolioMapper.toModel(portfolioDocumentRepository.save(document));
    }
    
    @Override
    public int getPortfolioCountByUserIdAndBrokerType(String userId, com.am.common.amcommondata.model.enums.BrokerType brokerType) {
        return (int) portfolioDocumentRepository.findByOwner(userId)
                .stream()
                .filter(p -> p.getBrokerType() == brokerType)
                .count();
    }

    @Override
    public List<String> getAllUserIds() {
        return portfolioDocumentRepository.findAllDistinctOwners();
    }
}
