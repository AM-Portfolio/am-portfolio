package com.portfolio.service;

import lombok.RequiredArgsConstructor;
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
}
