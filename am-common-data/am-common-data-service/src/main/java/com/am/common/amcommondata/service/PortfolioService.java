package com.am.common.amcommondata.service;

import com.am.common.amcommondata.model.PortfolioModelV1;

import java.util.List;
import java.util.UUID;

public interface PortfolioService {
    List<PortfolioModelV1> getPortfoliosByUserId(String userId);
    PortfolioModelV1 getPortfolioById(UUID id);
    PortfolioModelV1 createPortfolio(PortfolioModelV1 portfolio);
}
