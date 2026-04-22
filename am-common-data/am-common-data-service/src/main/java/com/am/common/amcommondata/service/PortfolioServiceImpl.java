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
        PortfolioDocument document = portfolioMapper.toDocument(portfolioModel);
        return portfolioMapper.toModel(portfolioDocumentRepository.save(document));
    }
}
