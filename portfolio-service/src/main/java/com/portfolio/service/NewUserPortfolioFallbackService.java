package com.portfolio.service;

import com.am.common.amcommondata.model.PortfolioModelV1;
import com.am.common.amcommondata.service.PortfolioService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

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
public class NewUserPortfolioFallbackService {

    private static final String DISMISSED_KEY_PREFIX = "demo:dismissed:";
    private static final String DEMO_DISPLAY_NAME = "Demo Portfolio";

    private final PortfolioService portfolioService;
    private final StringRedisTemplate redisTemplate;

    @Value("${app.demo.portfolio-id:}")
    private String demoPortfolioId;

    /**
     * Resolved owner/portfolio IDs for dashboard reads when the caller may be viewing the demo.
     * When eligible, remaps to the demo document's real owner so downstream queries hit Mongo data.
     */
    public record DemoResolution(String userId, String portfolioId) {}

    /** Lightweight list row for {@code GET /list} (keeps API DTO mapping out of the controller). */
    public record BasicPortfolioRow(
            String portfolioId,
            String portfolioName,
            String kind,
            Integer gapMissingCount,
            boolean dummy) {}

    // ---------------------------------------------------------------------------
    // Public API
    // ---------------------------------------------------------------------------

    /**
     * Remaps user/portfolio for summary, holdings, history, and intraday when the
     * authenticated user has zero real portfolios and is addressing the demo ID (or all portfolios).
     */
    public DemoResolution resolveRequest(String userId, String portfolioId) {
        if (!isConfigured()) {
            return new DemoResolution(userId, portfolioId);
        }

        List<PortfolioModelV1> real = portfolioService.getPortfoliosByUserId(userId);
        if (real == null || real.isEmpty()) {
            if (portfolioId == null || portfolioId.equals(demoPortfolioId)) {
                String demoOwner = getDemoOwner();
                if (demoOwner != null) {
                    return new DemoResolution(demoOwner, demoPortfolioId);
                }
            }
        }
        return new DemoResolution(userId, portfolioId);
    }

    /**
     * Returns real portfolios as list rows, or a single demo row when the user has none.
     */
    public List<BasicPortfolioRow> listBasicPortfolios(String userId) {
        List<PortfolioModelV1> portfolios = portfolioService.getPortfoliosByUserId(userId);

        if (portfolios == null || portfolios.isEmpty()) {
            PortfolioModelV1 demoModel = getDemoPortfolioForNewUser(userId, Collections.emptyList());
            if (demoModel == null) {
                return Collections.emptyList();
            }
            log.info("[DemoPortfolio] Injecting demo portfolio for new user: {}", userId);
            return Collections.singletonList(new BasicPortfolioRow(
                    demoModel.getId() != null ? demoModel.getId().toString() : null,
                    demoModel.getName() != null ? demoModel.getName() : DEMO_DISPLAY_NAME,
                    "BROKER",
                    null,
                    true));
        }

        return portfolios.stream()
                .map(portfolio -> new BasicPortfolioRow(
                        portfolio.getId() != null ? portfolio.getId().toString() : null,
                        portfolio.getName(),
                        portfolio.getPortfolioKind() != null ? portfolio.getPortfolioKind().name()
                                : (portfolio.getSourcePortfolioId() != null && !portfolio.getSourcePortfolioId().isBlank()
                                        ? "BASKET" : "BROKER"),
                        portfolio.getGapMissingCount(),
                        false))
                .collect(Collectors.toList());
    }

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
            .name(DEMO_DISPLAY_NAME)
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
            .build();
    }
}
