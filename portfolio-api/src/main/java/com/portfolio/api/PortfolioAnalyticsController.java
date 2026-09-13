package com.portfolio.api;

import com.portfolio.analytics.intelligence.PortfolioIntelligenceService;
import com.portfolio.analytics.service.providers.portfolio.PortfolioAnalyticsFacade;
import com.portfolio.api.security.PortfolioOwnerAssert;
import com.portfolio.model.analytics.intelligence.PortfolioIntelligenceResponse;
import com.portfolio.model.analytics.intelligence.ReportPreviewRequest;
import com.portfolio.model.analytics.intelligence.ReportPreviewResponse;
import com.portfolio.model.analytics.intelligence.StressRequest;
import com.portfolio.model.analytics.intelligence.StressResponse;
import com.portfolio.model.analytics.intelligence.WhatIfRequest;
import com.portfolio.model.analytics.intelligence.WhatIfResponse;
import com.portfolio.model.analytics.request.AdvancedAnalyticsRequest;
import com.portfolio.model.analytics.response.AdvancedAnalyticsResponse;

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
 * REST controller for portfolio analytics
 */
@RestController
@RequestMapping("/v1/analytics/portfolio")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Portfolio Analytics", description = "Advanced analytics endpoints for portfolio data")
public class PortfolioAnalyticsController {

    private final PortfolioAnalyticsFacade portfolioAnalyticsFacade;
    private final com.portfolio.service.PortfolioDashboardService portfolioDashboardService;
    private final PortfolioOwnerAssert portfolioOwnerAssert;
    private final PortfolioIntelligenceService portfolioIntelligenceService;

    /**
     * Advanced analytics endpoint that combines multiple analytics features with
     * timeframe support
     *
     * @param portfolioId The portfolio ID to analyze
     * @param request     The advanced analytics request parameters
     * @return Combined analytics data based on requested components
     */
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
        if (portfolioId == null || portfolioId.equals("undefined") || portfolioId.equals("null")) {
            log.warn("REST request for advanced analytics on invalid portfolio: {}", portfolioId);
            return ResponseEntity.badRequest().build();
        }

        portfolioOwnerAssert.requireOwner(portfolioId);

        if (request == null) {
            request = new AdvancedAnalyticsRequest();
        }
        if (request.getCoreIdentifiers() == null) {
            request.setCoreIdentifiers(new com.portfolio.model.analytics.request.CoreIdentifiers());
        }

        log.info("REST request for advanced analytics on portfolio: {} with timeframe: {} to {}",
                portfolioId, request.getTimeFrame());

        request.getCoreIdentifiers().setPortfolioId(portfolioId);

        AdvancedAnalyticsResponse response = portfolioAnalyticsFacade.calculateAdvancedAnalytics(request);

        if (response.getSummary() == null) {
            try {
                String userId = com.am.security.context.UserContext.getUserIdOrThrow();
                com.portfolio.model.TimeInterval interval = com.portfolio.model.TimeInterval.ONE_DAY;
                if (request.getTimeFrame() != null) {
                    interval = com.portfolio.model.TimeInterval.fromCode(request.getTimeFrame().name());
                }
                com.portfolio.model.portfolio.v1.PortfolioSummaryV1 summary =
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
        if (invalidPortfolioId(portfolioId)) {
            return ResponseEntity.badRequest().build();
        }
        var portfolio = portfolioOwnerAssert.requireOwner(portfolioId);
        log.info("REST request for intelligence on portfolio: {}", portfolioId);
        return ResponseEntity.ok(portfolioIntelligenceService.intelligence(portfolioId, portfolio));
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
        if (invalidPortfolioId(portfolioId)) {
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
        if (invalidPortfolioId(portfolioId)) {
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
        if (invalidPortfolioId(portfolioId)) {
            return ResponseEntity.badRequest().build();
        }
        var portfolio = portfolioOwnerAssert.requireOwner(portfolioId);
        log.info("REST request for report preview on portfolio: {}", portfolioId);
        return ResponseEntity.ok(portfolioIntelligenceService.reportPreview(portfolioId, request, portfolio));
    }

    private static boolean invalidPortfolioId(String portfolioId) {
        return portfolioId == null || portfolioId.equals("undefined") || portfolioId.equals("null");
    }
}
