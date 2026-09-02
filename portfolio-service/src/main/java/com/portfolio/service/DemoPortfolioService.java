package com.portfolio.service;

import com.am.common.amcommondata.model.PortfolioModelV1;
import com.am.common.amcommondata.service.PortfolioService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.List;
import java.util.UUID;

/**
 * Manages the shared "Demo Portfolio" shown to new users who have no portfolios yet.
 *
 * Strategy:
 *   1. Reads the shared demo portfolio from the DB using app.demo.portfolio-id.
 *   2. Returns a cloned, in-memory copy with isDummy=true overlaid on the user's context.
 *   3. Stores a per-user Redis flag when the user explicitly dismisses the demo so we stop
 *      showing it even if they still have 0 real portfolios.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class DemoPortfolioService {

    private static final String DISMISSED_KEY_PREFIX = "demo:dismissed:";
    private static final Duration DISMISSED_TTL = Duration.ofDays(365 * 10); // effectively forever

    private final PortfolioService portfolioService;
    private final StringRedisTemplate redisTemplate;

    @Value("${app.demo.portfolio-id:}")
    private String demoPortfolioId;

    // ---------------------------------------------------------------------------
    // Public API
    // ---------------------------------------------------------------------------

    /**
     * Returns the demo PortfolioModelV1 (in-memory clone with isDummy=true) if:
     *  - A DEMO_PORTFOLIO_ID is configured, AND
     *  - The user's real portfolio list is empty, AND
     *  - The user has not explicitly dismissed the demo.
     * Returns null otherwise.
     */
    public PortfolioModelV1 getDemoPortfolioForNewUser(String userId, List<?> realPortfolios) {
        if (!isConfigured() || !realPortfolios.isEmpty() || isDismissed(userId)) {
            return null;
        }
        PortfolioModelV1 demo = fetchDemoPortfolio();
        if (demo == null) {
            return null;
        }
        return cloneAsDemo(demo, userId);
    }

    /**
     * Returns the demo PortfolioModelV1 (in-memory clone with isDummy=true) if the
     * given portfolioId matches the configured demo ID and the user is still eligible.
     * Returns null if not eligible or not the demo ID.
     */
    public PortfolioModelV1 getDemoModelIfEligible(String userId, String portfolioId, List<?> realPortfolios) {
        if (!isConfigured() || !realPortfolios.isEmpty() || isDismissed(userId)) {
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
     * Marks the demo as dismissed for this user.
     * After this, all get methods will return null regardless of portfolio count.
     */
    public void dismissForUser(String userId) {
        String key = DISMISSED_KEY_PREFIX + userId;
        redisTemplate.opsForValue().set(key, "1", DISMISSED_TTL);
        log.info("[DemoPortfolio] User {} dismissed the demo portfolio", userId);
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

    private boolean isDismissed(String userId) {
        return Boolean.TRUE.equals(redisTemplate.hasKey(DISMISSED_KEY_PREFIX + userId));
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
            .name(source.getName())
            .description(source.getDescription())
            .owner(userId)
            .currency(source.getCurrency())
            .fundType(source.getFundType())
            .status(source.getStatus())
            .equityModels(source.getEquityModels())
            .totalValue(source.getTotalValue())
            .investmentAmount(source.getInvestmentAmount())
            .brokerType(source.getBrokerType())
            .portfolioKind(source.getPortfolioKind())
            .assetCount(source.getAssetCount())
            .createdAt(source.getCreatedAt())
            .updatedAt(source.getUpdatedAt())
            // We cannot set isDummy since PortfolioModelV1 doesn't have it.
            // PortfolioController will wrap it in PortfolioBasicInfo and setDummy(true).
            .build();
    }
}
