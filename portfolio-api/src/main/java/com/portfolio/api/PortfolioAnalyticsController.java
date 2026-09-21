package com.portfolio.api;

import com.am.common.amcommondata.model.PortfolioModelV1;
import com.am.security.context.UserContext;
import com.portfolio.analytics.intelligence.AggregatePortfolioKeys;
import com.portfolio.analytics.intelligence.AggregatePortfolioLoader;
import com.portfolio.analytics.intelligence.PortfolioIntelligenceService;
import com.portfolio.analytics.service.providers.portfolio.PortfolioAnalyticsFacade;
import com.portfolio.api.security.PortfolioOwnerAssert;
import com.portfolio.model.analytics.intelligence.IntelligenceSuggestResponse;
import com.portfolio.model.analytics.intelligence.PortfolioIntelligenceResponse;
import com.portfolio.model.analytics.intelligence.ReportPreviewRequest;
import com.portfolio.model.analytics.intelligence.ReportPreviewResponse;
import com.portfolio.model.analytics.intelligence.StressRequest;
import com.portfolio.model.analytics.intelligence.StressResponse;
import com.portfolio.model.analytics.intelligence.WhatIfRequest;
import com.portfolio.model.analytics.intelligence.WhatIfResponse;
import com.portfolio.model.analytics.request.AdvancedAnalyticsRequest;
import com.portfolio.model.analytics.request.CoreIdentifiers;
import com.portfolio.model.analytics.request.FeatureToggles;
import com.portfolio.model.analytics.response.AdvancedAnalyticsResponse;
import com.portfolio.model.TimeInterval;
import com.portfolio.model.portfolio.v1.PortfolioSummaryV1;
import com.portfolio.redis.service.PortfolioIntelligenceRedisService;
import com.portfolio.service.PortfolioDashboardService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * REST controller for portfolio analytics.
 * Literal {@code /all/**} routes are registered before {@code /{portfolioId}/**}.
 */
@RestController
@RequestMapping("/v1/analytics/portfolio")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Portfolio Analytics", description = "Advanced analytics endpoints for portfolio data")
public class PortfolioAnalyticsController {

    private final PortfolioAnalyticsFacade portfolioAnalyticsFacade;
    private final PortfolioDashboardService portfolioDashboardService;
    private final PortfolioOwnerAssert portfolioOwnerAssert;
    private final PortfolioIntelligenceService portfolioIntelligenceService;
    private final AggregatePortfolioLoader aggregatePortfolioLoader;
    private final PortfolioIntelligenceRedisService portfolioIntelligenceRedisService;

    // ── All-Portfolios aggregate (literal paths; before /{portfolioId}/**) ──

    @Operation(summary = "All-portfolios advanced analytics", operationId = "getAllPortfoliosAdvancedAnalytics")
    @PostMapping("/all/advanced")
    public ResponseEntity<AdvancedAnalyticsResponse> getAllAdvancedAnalytics(
            @RequestBody(required = false) AdvancedAnalyticsRequest request) {
        String userId = UserContext.getUserIdOrThrow();
        PortfolioModelV1 merged = aggregatePortfolioLoader.loadMerged(userId);
        request = normalizeAdvancedRequest(request, AggregatePortfolioKeys.RESPONSE_PORTFOLIO_ID);
        request.setPrefetchedPortfolio(merged);
        log.info("REST request for advanced analytics on ALL portfolios user={}", userId);

        AdvancedAnalyticsResponse response = portfolioAnalyticsFacade.calculateAdvancedAnalytics(request);
        if (response.getSummary() == null) {
            try {
                TimeInterval interval = TimeInterval.ONE_DAY;
                if (request.getTimeFrame() != null) {
                    interval = TimeInterval.fromCode(request.getTimeFrame().name());
                }
                PortfolioSummaryV1 summary = portfolioDashboardService.overviewPortfolio(userId, interval);
                if (summary != null) {
                    summary.setMarketCapHoldings(null);
                    summary.setSectorialHoldings(null);
                    summary.setBrokerPortfolios(null);
                    response.setSummary(summary);
                }
            } catch (Exception e) {
                log.warn("Failed to attach all-portfolios summary to advanced analytics", e);
            }
        }
        response.setPortfolioId(AggregatePortfolioKeys.RESPONSE_PORTFOLIO_ID);
        return ResponseEntity.ok(response);
    }

    @Operation(summary = "All-portfolios intelligence suggest", operationId = "suggestAllPortfoliosIntelligence")
    @GetMapping("/all/suggest")
    public ResponseEntity<IntelligenceSuggestResponse> suggestAll(
            @RequestParam String context,
            @RequestParam(required = false, defaultValue = "") String q,
            @RequestParam(required = false) String wire,
            @RequestParam(required = false, defaultValue = "8") int limit) {
        String userId = UserContext.getUserIdOrThrow();
        String cacheKey = AggregatePortfolioKeys.cacheKey(userId);
        PortfolioModelV1 merged = aggregatePortfolioLoader.loadMerged(userId);
        log.info("REST suggest ALL context={} q={}", context, q);
        return ResponseEntity.ok(
                portfolioIntelligenceService.suggest(cacheKey, context, q, wire, limit, merged));
    }

    @Operation(summary = "All-portfolios intelligence", operationId = "getAllPortfoliosIntelligence")
    @PostMapping("/all/intelligence")
    public ResponseEntity<PortfolioIntelligenceResponse> getAllIntelligence(
            @RequestBody(required = false) Object ignored) {
        String userId = UserContext.getUserIdOrThrow();
        String cacheKey = AggregatePortfolioKeys.cacheKey(userId);
        // Skip Mongo merge when Overview intel is already warm in Redis.
        var cached = portfolioIntelligenceRedisService.get(cacheKey);
        if (cached.isPresent()) {
            log.info("REST request for intelligence on ALL portfolios user={} cache=hit", userId);
            return ResponseEntity.ok(cached.get());
        }
        PortfolioModelV1 merged = aggregatePortfolioLoader.loadMerged(userId);
        log.info("REST request for intelligence on ALL portfolios user={} cache=miss", userId);
        return ResponseEntity.ok(portfolioIntelligenceService.intelligence(cacheKey, merged));
    }

    @Operation(summary = "All-portfolios stress", operationId = "runAllPortfoliosStress")
    @PostMapping("/all/stress")
    public ResponseEntity<StressResponse> runAllStress(
            @RequestBody(required = false) StressRequest request) {
        String userId = UserContext.getUserIdOrThrow();
        String cacheKey = AggregatePortfolioKeys.cacheKey(userId);
        PortfolioModelV1 merged = aggregatePortfolioLoader.loadMerged(userId);
        log.info("REST request for stress on ALL portfolios user={}", userId);
        return ResponseEntity.ok(portfolioIntelligenceService.stress(cacheKey, request, merged));
    }

    @Operation(summary = "All-portfolios what-if", operationId = "runAllPortfoliosWhatIf")
    @PostMapping("/all/what-if")
    public ResponseEntity<WhatIfResponse> runAllWhatIf(@RequestBody WhatIfRequest request) {
        String userId = UserContext.getUserIdOrThrow();
        String cacheKey = AggregatePortfolioKeys.cacheKey(userId);
        PortfolioModelV1 merged = aggregatePortfolioLoader.loadMerged(userId);
        log.info("REST request for what-if on ALL portfolios user={}", userId);
        return ResponseEntity.ok(portfolioIntelligenceService.whatIf(cacheKey, request, merged));
    }

    // ── Single portfolio ──

    @Operation(summary = "Get advanced portfolio analytics", description = "Retrieves comprehensive analytics for a portfolio with customizable components and timeframes. Requires portfolio ownership.", operationId = "getAdvancedPortfolioAnalytics")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Analytics data retrieved successfully", content = @Content(mediaType = "application/json", schema = @Schema(implementation = AdvancedAnalyticsResponse.class))),
            @ApiResponse(responseCode = "400", description = "Invalid request parameters"),
            @ApiResponse(responseCode = "403", description = "Caller is not the portfolio owner"),
            @ApiResponse(responseCode = "404", description = "Portfolio not found")
    })
    @PostMapping("/{portfolioId}/advanced")
    public ResponseEntity<AdvancedAnalyticsResponse> getAdvancedAnalytics(
            @PathVariable String portfolioId,
            @RequestBody(required = false) AdvancedAnalyticsRequest request) {
        if (isReservedAll(portfolioId) || invalidPortfolioId(portfolioId)) {
            log.warn("REST request for advanced analytics on invalid portfolio: {}", portfolioId);
            return ResponseEntity.badRequest().build();
        }

        portfolioOwnerAssert.requireOwner(portfolioId);
        request = normalizeAdvancedRequest(request, portfolioId);

        log.info("REST request for advanced analytics on portfolio: {} with timeframe: {} to {}",
                portfolioId, request.getTimeFrame());

        AdvancedAnalyticsResponse response = portfolioAnalyticsFacade.calculateAdvancedAnalytics(request);

        if (response.getSummary() == null) {
            try {
                String userId = UserContext.getUserIdOrThrow();
                TimeInterval interval = TimeInterval.ONE_DAY;
                if (request.getTimeFrame() != null) {
                    interval = TimeInterval.fromCode(request.getTimeFrame().name());
                }
                PortfolioSummaryV1 summary =
                        portfolioDashboardService.overviewPortfolio(userId, portfolioId, interval);
                if (summary != null) {
                    summary.setMarketCapHoldings(null);
                    summary.setSectorialHoldings(null);
                    summary.setBrokerPortfolios(null);

                    response.setSummary(summary);
                }
            } catch (Exception e) {
                log.warn("Failed to attach portfolio summary to advanced analytics response", e);
            }
        }

        return ResponseEntity.ok(response);
    }

    @Operation(summary = "Portfolio intelligence", description = "Health, Risk, and X-Ray summary. Requires portfolio ownership.", operationId = "getPortfolioIntelligence")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Intelligence computed", content = @Content(mediaType = "application/json", schema = @Schema(implementation = PortfolioIntelligenceResponse.class))),
            @ApiResponse(responseCode = "400", description = "Invalid portfolioId"),
            @ApiResponse(responseCode = "401", description = "Unauthenticated"),
            @ApiResponse(responseCode = "403", description = "Caller is not the portfolio owner"),
            @ApiResponse(responseCode = "404", description = "Portfolio not found")
    })
    @PostMapping("/{portfolioId}/intelligence")
    public ResponseEntity<PortfolioIntelligenceResponse> getIntelligence(
            @PathVariable String portfolioId,
            @RequestBody(required = false) Object ignored) {
        if (isReservedAll(portfolioId) || invalidPortfolioId(portfolioId)) {
            return ResponseEntity.badRequest().build();
        }
        var portfolio = portfolioOwnerAssert.requireOwner(portfolioId);
        log.info("REST request for intelligence on portfolio: {}", portfolioId);
        return ResponseEntity.ok(portfolioIntelligenceService.intelligence(portfolioId, portfolio));
    }

    @Operation(
            summary = "Intelligence typeahead suggest",
            description = "Context-aware suggestions: STRESS_SECTOR | WHAT_IF_SYMBOL | WHAT_IF_SECTOR | CLASS_ADD_NAME",
            operationId = "suggestPortfolioIntelligence")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Suggestions", content = @Content(mediaType = "application/json", schema = @Schema(implementation = IntelligenceSuggestResponse.class))),
            @ApiResponse(responseCode = "401", description = "Unauthenticated"),
            @ApiResponse(responseCode = "403", description = "Caller is not the portfolio owner"),
            @ApiResponse(responseCode = "404", description = "Portfolio not found")
    })
    @GetMapping("/{portfolioId}/suggest")
    public ResponseEntity<IntelligenceSuggestResponse> suggest(
            @PathVariable String portfolioId,
            @RequestParam String context,
            @RequestParam(required = false, defaultValue = "") String q,
            @RequestParam(required = false) String wire,
            @RequestParam(required = false, defaultValue = "8") int limit) {
        if (isReservedAll(portfolioId) || invalidPortfolioId(portfolioId)) {
            return ResponseEntity.badRequest().build();
        }
        var portfolio = portfolioOwnerAssert.requireOwner(portfolioId);
        log.info("REST suggest portfolio={} context={} q={}", portfolioId, context, q);
        return ResponseEntity.ok(
                portfolioIntelligenceService.suggest(portfolioId, context, q, wire, limit, portfolio));
    }

    @Operation(summary = "Stress scenarios", description = "Scenario estimate shocks. Requires portfolio ownership. No Mongo writes. Supports single preset or presets[] batch.", operationId = "runPortfolioStress")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Stress estimate", content = @Content(mediaType = "application/json", schema = @Schema(implementation = StressResponse.class))),
            @ApiResponse(responseCode = "400", description = "Invalid preset or custom shock"),
            @ApiResponse(responseCode = "401", description = "Unauthenticated"),
            @ApiResponse(responseCode = "403", description = "Caller is not the portfolio owner"),
            @ApiResponse(responseCode = "404", description = "Portfolio not found")
    })
    @PostMapping("/{portfolioId}/stress")
    public ResponseEntity<StressResponse> runStress(
            @PathVariable String portfolioId,
            @RequestBody(required = false) StressRequest request) {
        if (isReservedAll(portfolioId) || invalidPortfolioId(portfolioId)) {
            return ResponseEntity.badRequest().build();
        }
        var portfolio = portfolioOwnerAssert.requireOwner(portfolioId);
        log.info("REST request for stress on portfolio: {}", portfolioId);
        return ResponseEntity.ok(portfolioIntelligenceService.stress(portfolioId, request, portfolio));
    }

    @Operation(summary = "What-if simulation", description = "Stateless before/after health and weights. Requires portfolio ownership. No Mongo writes.", operationId = "runPortfolioWhatIf")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "What-if result", content = @Content(mediaType = "application/json", schema = @Schema(implementation = WhatIfResponse.class))),
            @ApiResponse(responseCode = "400", description = "Invalid mode or parameters"),
            @ApiResponse(responseCode = "401", description = "Unauthenticated"),
            @ApiResponse(responseCode = "403", description = "Caller is not the portfolio owner"),
            @ApiResponse(responseCode = "404", description = "Portfolio not found")
    })
    @PostMapping("/{portfolioId}/what-if")
    public ResponseEntity<WhatIfResponse> runWhatIf(
            @PathVariable String portfolioId,
            @RequestBody WhatIfRequest request) {
        if (isReservedAll(portfolioId) || invalidPortfolioId(portfolioId)) {
            return ResponseEntity.badRequest().build();
        }
        var portfolio = portfolioOwnerAssert.requireOwner(portfolioId);
        log.info("REST request for what-if on portfolio: {}", portfolioId);
        return ResponseEntity.ok(portfolioIntelligenceService.whatIf(portfolioId, request, portfolio));
    }

    @Operation(summary = "Report preview", description = "Weekly/monthly JSON payload for future PDF. Requires portfolio ownership. No PDF/email.", operationId = "getPortfolioReportPreview")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Report preview", content = @Content(mediaType = "application/json", schema = @Schema(implementation = ReportPreviewResponse.class))),
            @ApiResponse(responseCode = "400", description = "Invalid period"),
            @ApiResponse(responseCode = "401", description = "Unauthenticated"),
            @ApiResponse(responseCode = "403", description = "Caller is not the portfolio owner"),
            @ApiResponse(responseCode = "404", description = "Portfolio not found")
    })
    @PostMapping("/{portfolioId}/report/preview")
    public ResponseEntity<ReportPreviewResponse> reportPreview(
            @PathVariable String portfolioId,
            @RequestBody(required = false) ReportPreviewRequest request) {
        if (isReservedAll(portfolioId) || invalidPortfolioId(portfolioId)) {
            return ResponseEntity.badRequest().build();
        }
        var portfolio = portfolioOwnerAssert.requireOwner(portfolioId);
        log.info("REST request for report preview on portfolio: {}", portfolioId);
        return ResponseEntity.ok(portfolioIntelligenceService.reportPreview(portfolioId, request, portfolio));
    }

    private static AdvancedAnalyticsRequest normalizeAdvancedRequest(
            AdvancedAnalyticsRequest request, String portfolioId) {
        if (request == null) {
            request = new AdvancedAnalyticsRequest();
        }
        if (request.getCoreIdentifiers() == null) {
            request.setCoreIdentifiers(new CoreIdentifiers());
        }
        if (request.getFeatureToggles() == null) {
            request.setFeatureToggles(new FeatureToggles());
        }
        var toggles = request.getFeatureToggles();
        if (!toggles.isIncludeHeatmap()
                && !toggles.isIncludeMovers()
                && !toggles.isIncludeSectorAllocation()
                && !toggles.isIncludeMarketCapAllocation()) {
            toggles.setIncludeHeatmap(true);
            toggles.setIncludeMovers(true);
            toggles.setIncludeSectorAllocation(true);
            toggles.setIncludeMarketCapAllocation(true);
        }
        request.getCoreIdentifiers().setPortfolioId(portfolioId);
        return request;
    }

    private static boolean isReservedAll(String portfolioId) {
        return portfolioId != null && portfolioId.equalsIgnoreCase("all");
    }

    private static boolean invalidPortfolioId(String portfolioId) {
        return portfolioId == null || portfolioId.equals("undefined") || portfolioId.equals("null");
    }
}
