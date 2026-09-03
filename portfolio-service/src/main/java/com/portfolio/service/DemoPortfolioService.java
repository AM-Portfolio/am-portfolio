package com.portfolio.service;

import com.am.common.amcommondata.model.PortfolioModelV1;
import com.am.common.amcommondata.service.PortfolioService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

/**
 * Manages the shared "Demo Portfolio" shown to new users who have no portfolios yet.
 *
 * Strategy:
 *   1. Reads the shared demo portfolio from the DB using app.demo.portfolio-id.
 *   2. Returns a cloned, in-memory copy with name "Demo Portfolio" and owner = current user.
 *   3. Demo is shown only while the user has zero real portfolios. Uploading/linking a
 *      real portfolio automatically hides it. There is no separate "dismiss" path —
 *      legacy Redis dismiss keys are cleared and ignored so modules stay in sync.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class DemoPortfolioService {

    private static final String DISMISSED_KEY_PREFIX = "demo:dismissed:";

    private final PortfolioService portfolioService;
    private final StringRedisTemplate redisTemplate;

    @Value("${app.demo.portfolio-id:}")
    private String demoPortfolioId;

    // ---------------------------------------------------------------------------
    // Public API
    // ---------------------------------------------------------------------------

    /**
     * Returns the demo PortfolioModelV1 (in-memory clone) if:
     *  - A DEMO_PORTFOLIO_ID is configured, AND
     *  - The user's real portfolio list is empty.
     * Returns null otherwise.
     */
    public PortfolioModelV1 getDemoPortfolioForNewUser(String userId, List<?> realPortfolios) {
        if (!isConfigured() || !realPortfolios.isEmpty()) {
            return null;
        }
        PortfolioModelV1 demo = fetchDemoPortfolio();
        if (demo == null) {
            return null;
        }
        return cloneAsDemo(demo, userId);
    }

    /**
     * Returns the demo PortfolioModelV1 (in-memory clone) if the
     * given portfolioId matches the configured demo ID and the user still has 0 real portfolios.
     * Returns null if not eligible or not the demo ID.
     */
    public PortfolioModelV1 getDemoModelIfEligible(String userId, String portfolioId, List<?> realPortfolios) {
        if (!isConfigured() || !realPortfolios.isEmpty()) {
            return null;
        }
        if (!demoPortfolioId.equals(portfolioId)) {
            return null;
        }
        PortfolioModelV1 demo = fetchDemoPortfolio();
        if (demo == null) {
            return null;
        }
        return cloneAsDemo(demo, userId);
    }

    /**
     * Legacy no-op restore: clears any old per-user dismiss flag so demo injection
     * stays consistent across portfolio / trade / analysis. Demo is only hidden when
     * the user has at least one real portfolio.
     */
    public void dismissForUser(String userId) {
        String key = DISMISSED_KEY_PREFIX + userId;
        try {
            Boolean removed = redisTemplate.delete(key);
            log.info("[DemoPortfolio] Cleared legacy dismiss flag for user {} (removed={})", userId, removed);
        } catch (Exception e) {
            log.warn("[DemoPortfolio] Failed to clear dismiss flag for {}: {}", userId, e.getMessage());
        }
    }

    /** Returns the configured demo portfolio ID, or blank if not configured. */
    public String getDemoPortfolioId() {
        return demoPortfolioId;
    }

    public String getDemoOwner() {
        PortfolioModelV1 demo = fetchDemoPortfolio();
        return demo != null ? demo.getOwner() : null;
    }

    // ---------------------------------------------------------------------------
    // Private helpers
    // ---------------------------------------------------------------------------

    private boolean isConfigured() {
        return demoPortfolioId != null && !demoPortfolioId.isBlank();
    }

    private PortfolioModelV1 fetchDemoPortfolio() {
        try {
            return portfolioService.getPortfolioById(UUID.fromString(demoPortfolioId));
        } catch (Exception e) {
            log.warn("[DemoPortfolio] Could not fetch demo portfolio id={}: {}", demoPortfolioId, e.getMessage());
            return null;
        }
    }

    private PortfolioModelV1 cloneAsDemo(PortfolioModelV1 source, String userId) {
        return PortfolioModelV1.builder()
            .id(source.getId())
            .name("Demo Portfolio")
            .description(source.getDescription())
            .owner(userId)
            .currency(source.getCurrency())
            .fundType(source.getFundType())
            .status(source.getStatus())
            .equityModels(source.getEquityModels())
            .totalValue(source.getTotalValue())
            .investmentAmount(source.getInvestmentAmount())
            .brokerType(source.getBrokerType())
            .portfolioKind(com.am.common.amcommondata.model.enums.PortfolioKind.BROKER)
            .assetCount(source.getAssetCount())
            .createdAt(source.getCreatedAt())
            .updatedAt(source.getUpdatedAt())
            // We cannot set isDummy since PortfolioModelV1 doesn't have it.
            // PortfolioController will wrap it in PortfolioBasicInfo and setDummy(true).
            .build();
    }
}
