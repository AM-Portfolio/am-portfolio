package com.portfolio.service;

import com.am.common.amcommondata.model.PortfolioModelV1;
import com.am.common.amcommondata.model.enums.PortfolioKind;
import com.am.common.amcommondata.service.PortfolioService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Per-user Demo Account provisioner.
 *
 * <p>The configured {@code app.demo.portfolio-id} is a read-only template. Eligible users
 * get a personal {@link PortfolioKind#DEMO} clone they own, so intel/heatmap ownership
 * checks succeed and edits stay isolated.
 *
 * <p>Eligibility = no real broker book ({@link PortfolioKind#isRealBrokerBook}).
 * Paper and baskets never hide Demo. When a real broker appears, personal DEMO is
 * soft-deleted; when the last real broker is removed, Demo is re-cloned from the template.
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
     * Resolved owner/portfolio IDs for dashboard reads.
     * Personal DEMO keeps {@code userId} as owner (no shared-template remap).
     */
    public record DemoResolution(String userId, String portfolioId) {}

    /** Lightweight list row for {@code GET /list} (keeps API DTO mapping out of the controller). */
    public record BasicPortfolioRow(
            String portfolioId,
            String portfolioName,
            String kind,
            Integer gapMissingCount,
            boolean dummy) {}

    public DemoResolution resolveRequest(String userId, String portfolioId) {
        if (!isConfigured() || userId == null || userId.isBlank()) {
            return new DemoResolution(userId, portfolioId);
        }

        PortfolioModelV1 personal = ensurePersonalDemoLifecycle(userId);
        if (personal == null || personal.getId() == null) {
            return new DemoResolution(userId, portfolioId);
        }

        String personalId = personal.getId().toString();
        if (portfolioId == null
                || portfolioId.isBlank()
                || portfolioId.equals(personalId)
                || portfolioId.equals(demoPortfolioId)) {
            return new DemoResolution(userId, personalId);
        }
        return new DemoResolution(userId, portfolioId);
    }

    public List<BasicPortfolioRow> listBasicPortfolios(String userId) {
        ensurePersonalDemoLifecycle(userId);
        List<PortfolioModelV1> portfolios = portfolioService.getPortfoliosByUserId(userId);
        if (portfolios == null) {
            portfolios = Collections.emptyList();
        }

        return portfolios.stream()
                .map(portfolio -> {
                    boolean demo = PortfolioKind.isDemo(portfolio.getPortfolioKind());
                    String kind = portfolio.getPortfolioKind() != null
                            ? portfolio.getPortfolioKind().name()
                            : (portfolio.getSourcePortfolioId() != null && !portfolio.getSourcePortfolioId().isBlank()
                                    ? "BASKET" : "BROKER");
                    return new BasicPortfolioRow(
                            portfolio.getId() != null ? portfolio.getId().toString() : null,
                            demo ? DEMO_DISPLAY_NAME
                                    : (portfolio.getName() != null ? portfolio.getName() : null),
                            kind,
                            portfolio.getGapMissingCount(),
                            demo);
                })
                .collect(Collectors.toList());
    }

    /**
     * Returns the user's personal DEMO when eligible (creates on first need).
     * {@code realPortfolios} is ignored for eligibility; kept for call-site compatibility.
     */
    public PortfolioModelV1 getDemoPortfolioForNewUser(String userId, List<?> realPortfolios) {
        return ensurePersonalDemoLifecycle(userId);
    }

    /**
     * Returns the personal DEMO model when {@code portfolioId} addresses it (or the legacy
     * shared template id) and the user is still eligible.
     */
    public PortfolioModelV1 getDemoModelIfEligible(String userId, String portfolioId, List<?> realPortfolios) {
        PortfolioModelV1 personal = ensurePersonalDemoLifecycle(userId);
        if (personal == null || personal.getId() == null || portfolioId == null) {
            return null;
        }
        String personalId = personal.getId().toString();
        if (portfolioId.equals(personalId) || portfolioId.equals(demoPortfolioId)) {
            return personal;
        }
        return null;
    }

    /**
     * Legacy no-op restore: clears any old per-user dismiss flag.
     * Demo visibility is driven by real-broker eligibility only.
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

    /** Returns the configured demo template portfolio ID, or blank if not configured. */
    public String getDemoPortfolioId() {
        return demoPortfolioId;
    }

    /** Owner of the shared template (ops / seed account), not the end user. */
    public String getDemoOwner() {
        PortfolioModelV1 demo = fetchTemplate();
        return demo != null ? demo.getOwner() : null;
    }

    /**
     * Soft-hide personal DEMO when a real broker book exists; otherwise find-or-create.
     * @return active personal DEMO, or null when ineligible / unconfigured / template missing
     */
    PortfolioModelV1 ensurePersonalDemoLifecycle(String userId) {
        if (!isConfigured() || userId == null || userId.isBlank()) {
            return null;
        }

        List<PortfolioModelV1> owned = portfolioService.getPortfoliosByUserId(userId);
        if (owned == null) {
            owned = Collections.emptyList();
        }

        if (hasRealBrokerBook(owned)) {
            softHidePersonalDemos(userId, owned);
            return null;
        }

        List<PortfolioModelV1> existingDemos = owned.stream()
                .filter(p -> PortfolioKind.isDemo(p.getPortfolioKind()))
                .sorted(NewUserPortfolioFallbackService::compareDemoNewestFirst)
                .collect(Collectors.toList());
        if (!existingDemos.isEmpty()) {
            // Unique index may lag on first deploys / races — keep newest DEMO, soft-hide extras.
            PortfolioModelV1 survivor = existingDemos.get(0);
            if (existingDemos.size() > 1) {
                softHideExtraDemos(userId, existingDemos);
            }
            return decorateDemo(survivor);
        }

        return clonePersonalDemo(userId);
    }

    /** Newest updatedAt, then createdAt, then id — deterministic survivor. */
    static int compareDemoNewestFirst(PortfolioModelV1 a, PortfolioModelV1 b) {
        java.time.LocalDateTime aUp = a != null ? a.getUpdatedAt() : null;
        java.time.LocalDateTime bUp = b != null ? b.getUpdatedAt() : null;
        int byUpdated = java.util.Comparator.nullsLast(java.time.LocalDateTime::compareTo)
                .compare(bUp, aUp);
        if (byUpdated != 0) {
            return byUpdated;
        }
        java.time.LocalDateTime aCr = a != null ? a.getCreatedAt() : null;
        java.time.LocalDateTime bCr = b != null ? b.getCreatedAt() : null;
        int byCreated = java.util.Comparator.nullsLast(java.time.LocalDateTime::compareTo)
                .compare(bCr, aCr);
        if (byCreated != 0) {
            return byCreated;
        }
        String aId = a != null && a.getId() != null ? a.getId().toString() : "";
        String bId = b != null && b.getId() != null ? b.getId().toString() : "";
        return bId.compareTo(aId);
    }

    private void softHideExtraDemos(String userId, List<PortfolioModelV1> demosNewestFirst) {
        for (int i = 1; i < demosNewestFirst.size(); i++) {
            PortfolioModelV1 extra = demosNewestFirst.get(i);
            if (extra == null || extra.getId() == null) {
                continue;
            }
            try {
                portfolioService.softDeletePortfolio(extra.getId().toString(), userId);
                log.info("[DemoPortfolio] Soft-hid duplicate DEMO {} for user {} (kept newest)",
                        extra.getId(), userId);
            } catch (Exception e) {
                log.warn("[DemoPortfolio] Failed to soft-hide duplicate DEMO {} for {}: {}",
                        extra.getId(), userId, e.getMessage());
            }
        }
    }

    static boolean hasRealBrokerBook(List<PortfolioModelV1> portfolios) {
        if (portfolios == null) {
            return false;
        }
        for (PortfolioModelV1 p : portfolios) {
            if (p == null) {
                continue;
            }
            if (PortfolioKind.isRealBrokerBook(p.getPortfolioKind(), p.getBrokerType())) {
                return true;
            }
        }
        return false;
    }

    private void softHidePersonalDemos(String userId, List<PortfolioModelV1> owned) {
        for (PortfolioModelV1 p : owned) {
            if (p == null || p.getId() == null || !PortfolioKind.isDemo(p.getPortfolioKind())) {
                continue;
            }
            try {
                portfolioService.softDeletePortfolio(p.getId().toString(), userId);
                log.info("[DemoPortfolio] Soft-hid DEMO {} for user {} (real broker present)",
                        p.getId(), userId);
            } catch (Exception e) {
                log.warn("[DemoPortfolio] Failed to soft-hide DEMO {} for {}: {}",
                        p.getId(), userId, e.getMessage());
            }
        }
    }

    private PortfolioModelV1 clonePersonalDemo(String userId) {
        PortfolioModelV1 template = fetchTemplate();
        if (template == null) {
            return null;
        }

        PortfolioModelV1 clone = PortfolioModelV1.builder()
                .id(UUID.randomUUID())
                .name(DEMO_DISPLAY_NAME)
                .description(template.getDescription())
                .owner(userId)
                .currency(template.getCurrency())
                .fundType(template.getFundType())
                .status(template.getStatus())
                .equityModels(template.getEquityModels())
                .mutualFunds(template.getMutualFunds())
                .bonds(template.getBonds())
                .commodities(template.getCommodities())
                .cash(template.getCash())
                .totalValue(template.getTotalValue())
                .investmentAmount(template.getInvestmentAmount())
                .brokerType(null)
                .portfolioKind(PortfolioKind.DEMO)
                .assetCount(template.getAssetCount())
                .createdBy(userId)
                .build();

        try {
            PortfolioModelV1 saved = portfolioService.createPortfolio(clone);
            log.info("[DemoPortfolio] Cloned personal DEMO id={} for user={} from template={}",
                    saved != null && saved.getId() != null ? saved.getId() : null,
                    userId,
                    demoPortfolioId);
            return decorateDemo(saved);
        } catch (DuplicateKeyException dup) {
            List<PortfolioModelV1> raced =
                    portfolioService.findByOwnerAndPortfolioKind(userId, PortfolioKind.DEMO);
            if (!raced.isEmpty()) {
                return decorateDemo(raced.get(0));
            }
            log.warn("[DemoPortfolio] DuplicateKey cloning DEMO for {} but none found on re-read", userId);
            return null;
        } catch (Exception e) {
            log.warn("[DemoPortfolio] Failed to clone DEMO for {}: {}", userId, e.getMessage());
            return null;
        }
    }

    private PortfolioModelV1 decorateDemo(PortfolioModelV1 demo) {
        if (demo == null) {
            return null;
        }
        demo.setName(DEMO_DISPLAY_NAME);
        demo.setPortfolioKind(PortfolioKind.DEMO);
        return demo;
    }

    private boolean isConfigured() {
        return demoPortfolioId != null && !demoPortfolioId.isBlank();
    }

    private PortfolioModelV1 fetchTemplate() {
        try {
            return portfolioService.getPortfolioById(UUID.fromString(demoPortfolioId));
        } catch (Exception e) {
            log.warn("[DemoPortfolio] Could not fetch demo template id={}: {}", demoPortfolioId, e.getMessage());
            return null;
        }
    }
}
