package com.portfolio.service.portfolio;

import com.am.common.amcommondata.model.PortfolioModelV1;
import com.am.common.amcommondata.service.PortfolioService;
import com.portfolio.redis.service.PortfolioHoldingsRedisService;
import com.portfolio.redis.service.PortfolioIntelligenceRedisService;
import com.portfolio.redis.service.PortfolioSummaryRedisService;
import com.portfolio.service.event.PortfolioDeleteNotifier;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.UUID;

/**
 * Synchronous broker-portfolio delete: Mongo + caches + outbound DELETE fan-out.
 * IDs come only from the caller (path / JWT); no hardcoded portfolio or user ids.
 */
@Service
@Slf4j
public class BrokerPortfolioDeleteService {

    private static final String DELETE_SOURCE = "PORTFOLIO_HTTP_DELETE";

    private final PortfolioService portfolioService;
    private final PortfolioHoldingsRedisService holdingsRedisService;
    private final PortfolioSummaryRedisService summaryRedisService;
    private final PortfolioIntelligenceRedisService intelligenceRedisService;
    @Nullable
    private final PortfolioDeleteNotifier deleteNotifier;

    public BrokerPortfolioDeleteService(
            PortfolioService portfolioService,
            PortfolioHoldingsRedisService holdingsRedisService,
            PortfolioSummaryRedisService summaryRedisService,
            PortfolioIntelligenceRedisService intelligenceRedisService,
            @Autowired(required = false) PortfolioDeleteNotifier deleteNotifier) {
        this.portfolioService = portfolioService;
        this.holdingsRedisService = holdingsRedisService;
        this.summaryRedisService = summaryRedisService;
        this.intelligenceRedisService = intelligenceRedisService;
        this.deleteNotifier = deleteNotifier;
    }

    /**
     * @param portfolioId path UUID
     * @param ownerId     authenticated user id
     */
    public void deleteOwnedPortfolio(String portfolioId, String ownerId) {
        if (ownerId == null || ownerId.isBlank()) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Missing user");
        }
        final UUID id;
        try {
            id = UUID.fromString(portfolioId);
        } catch (IllegalArgumentException | NullPointerException ex) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid portfolioId");
        }

        PortfolioModelV1 portfolio = portfolioService.getPortfolioById(id);
        if (portfolio == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Portfolio not found");
        }
        if (!ownerId.equals(portfolio.getOwner())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Not owner of portfolio");
        }

        String name = portfolio.getName();
        String idStr = id.toString();

        portfolioService.deletePortfolioByIdAndOwner(idStr, ownerId);

        holdingsRedisService.evictPortfolioHoldings(ownerId, idStr);
        summaryRedisService.evictPortfolioSummary(ownerId, idStr);
        if (name != null && !name.isBlank() && !name.equals(idStr)) {
            holdingsRedisService.evictPortfolioHoldings(ownerId, name);
            summaryRedisService.evictPortfolioSummary(ownerId, name);
        }
        intelligenceRedisService.evict(idStr);
        intelligenceRedisService.evictAggregateForUser(ownerId);

        if (deleteNotifier != null) {
            deleteNotifier.notifyDeleted(ownerId, idStr, name, DELETE_SOURCE);
        } else {
            log.warn("PortfolioDeleteNotifier unavailable — Mongo deleted but outbound DELETE not published portfolioId={}",
                    idStr);
        }

        log.info("Broker portfolio deleted portfolioId={} ownerId={} name={}", idStr, ownerId, name);
    }
}
