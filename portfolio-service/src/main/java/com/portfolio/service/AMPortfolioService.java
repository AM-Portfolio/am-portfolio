package com.portfolio.service;

import lombok.RequiredArgsConstructor;

import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;
import com.am.common.amcommondata.model.PortfolioModel;
import com.am.common.amcommondata.service.PortfolioService;

@Service
@RequiredArgsConstructor
public class AMPortfolioService {
    private final PortfolioService portfolioService;

    public List<PortfolioModel> getPortfolios(String userId) {
        return portfolioService.getPortfoliosByUserId(userId);
    }

    public PortfolioModel getPortfolioById(UUID portfolioId) {
        return portfolioService.getPortfolioById(portfolioId);
    }

}
