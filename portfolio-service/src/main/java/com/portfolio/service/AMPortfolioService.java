package com.portfolio.service;

import lombok.RequiredArgsConstructor;

import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;
import com.am.common.amcommondata.model.PortfolioModel;
import com.am.common.amcommondata.service.PortfolioService;
import com.portfolio.kafka.model.PortfolioUpdateEvent;
import com.portfolio.mapper.PortfolioMapper;

@Service
@RequiredArgsConstructor
public class AMPortfolioService {
    private final PortfolioMapper portfolioMapper;
    private final PortfolioService portfolioService;

    public PortfolioModel processMessage(PortfolioUpdateEvent event) {
        PortfolioModel portfolioModel = portfolioMapper.toPortfolioModel(event);
        var portfolio = portfolioService.createPortfolio(portfolioModel);
        return portfolio;
    }

    public List<PortfolioModel> getPortfolios(String userId) {
        return portfolioService.getPortfoliosByUserId(userId);
    }

    public PortfolioModel getPortfolioById(UUID portfolioId) {
        return portfolioService.getPortfolioById(portfolioId);
    }

}
