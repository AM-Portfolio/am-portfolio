package com.am.common.amcommondata.service;

import com.am.common.amcommondata.model.PortfolioModelV1;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public interface PortfolioService {
    List<PortfolioModelV1> getPortfoliosByUserId(String userId);
    PortfolioModelV1 getPortfolioById(UUID id);
    PortfolioModelV1 createPortfolio(PortfolioModelV1 portfolio);
    PortfolioModelV1 upsertDocumentPortfolio(PortfolioModelV1 portfolioModel);
    List<String> getAllUserIds();
    List<String> getActiveUserIds(LocalDate cutoffDate); // Returns only users active since cutoffDate
    PortfolioModelV1 updateTradePortfolio(PortfolioModelV1 portfolioModel);
    void updateLastLoginDate(String userId, LocalDate loginDate);
}
