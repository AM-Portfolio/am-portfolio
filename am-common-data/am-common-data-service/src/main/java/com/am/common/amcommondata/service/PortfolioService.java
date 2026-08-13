package com.am.common.amcommondata.service;

import com.am.common.amcommondata.model.PortfolioModelV1;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public interface PortfolioService {
    List<PortfolioModelV1> getPortfoliosByUserId(String userId);
    PortfolioModelV1 getPortfolioById(UUID id);
    void deletePortfolioByIdAndOwner(String id, String owner);
    PortfolioModelV1 createPortfolio(PortfolioModelV1 portfolio);
    PortfolioModelV1 createBasketPortfolio(PortfolioModelV1 portfolio);
    PortfolioModelV1 upsertDocumentPortfolio(PortfolioModelV1 portfolioModel);
    PortfolioModelV1 savePortfolioDocument(PortfolioModelV1 portfolioModel);
    double getAllocatedQuantity(PortfolioModelV1 brokerPortfolio, String isin);
    double getAvailableQuantity(PortfolioModelV1 brokerPortfolio, String isin, Double rawQuantity);
    List<String> getAllUserIds();
    List<String> getActiveUserIds(LocalDate cutoffDate); // Returns only users active since cutoffDate
    PortfolioModelV1 updateTradePortfolio(PortfolioModelV1 portfolioModel);
    void updateLastLoginDate(String userId, LocalDate loginDate);
}
