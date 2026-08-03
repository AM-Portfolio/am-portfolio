package com.portfolio.api;

import com.am.common.amcommondata.model.PortfolioModelV1;
import com.am.common.amcommondata.service.PortfolioService;
import com.portfolio.api.model.PortfolioBasicInfo;
import com.portfolio.model.TimeInterval;
import com.portfolio.model.portfolio.PortfolioAnalysis;
import com.portfolio.model.portfolio.PortfolioHoldings;
import com.portfolio.model.portfolio.v1.PortfolioSummaryV1;
import com.portfolio.service.PortfolioDashboardService;
import com.portfolio.service.scheduler.PortfolioHistoryScheduler;
import com.am.common.amcommondata.model.PortfolioSnapshotModel;
import com.am.common.amcommondata.service.PortfolioSnapshotService;
import com.portfolio.service.scheduler.SnapshotCatchUpService;

import io.swagger.v3.oas.annotations.Hidden;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;
import java.time.LocalDate;

@RestController
@RequestMapping("/v1/portfolios")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Portfolio Management", description = "Endpoints for managing user portfolios")
public class PortfolioController {

    private final PortfolioDashboardService portfolioDashboardService;
    private final PortfolioService portfolioService;
    private final PortfolioHistoryScheduler portfolioHistoryScheduler;
    private final PortfolioSnapshotService portfolioSnapshotService;
    private final SnapshotCatchUpService snapshotCatchUpService;
    private final com.portfolio.service.portfolio.PortfolioIntradayService portfolioIntradayService;

    @org.springframework.beans.factory.annotation.Value("${app.jwt.internal-secret}")
    private String internalSecret;

    @Operation(summary = "Get intraday data for all portfolios")
    @GetMapping("/intraday")
    public ResponseEntity<List<com.portfolio.model.portfolio.IntradayDataPoint>> getAllPortfoliosIntraday() {
        String userId = com.am.security.context.UserContext.getUserIdOrThrow();
        String traceId = org.slf4j.MDC.get("traceId");
        log.info("[Intraday] Request for all portfolios, user={}, traceId={}", userId, traceId);
        return ResponseEntity.ok(portfolioIntradayService.getIntraday(userId, null));
    }

    @Operation(summary = "Get intraday data for specific portfolio")
    @GetMapping("/{portfolioId}/intraday")
    public ResponseEntity<List<com.portfolio.model.portfolio.IntradayDataPoint>> getPortfolioIntraday(
            @PathVariable String portfolioId) {
        String userId = com.am.security.context.UserContext.getUserIdOrThrow();
        String traceId = org.slf4j.MDC.get("traceId");
        log.info("[Intraday] Request for portfolio={}, user={}, traceId={}", portfolioId, userId, traceId);
        return ResponseEntity.ok(portfolioIntradayService.getIntraday(userId, portfolioId));
    }

    @Operation(summary = "Get portfolio by ID", description = "Retrieves detailed portfolio information for a specific portfolio ID")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Portfolio found", content = @Content(mediaType = "application/json", schema = @Schema(implementation = PortfolioModelV1.class))),
            @ApiResponse(responseCode = "400", description = "Invalid portfolio ID format"),
            @ApiResponse(responseCode = "404", description = "Portfolio not found")
    })
    @GetMapping("/{portfolioId}")
    public ResponseEntity<PortfolioModelV1> getPortfolioById(
            @Parameter(description = "Portfolio ID (UUID format)") @PathVariable String portfolioId) {
        log.info("PortfolioController - getPortfolioById called with portfolioId: {}", portfolioId);

        try {
            PortfolioModelV1 portfolio = portfolioService.getPortfolioById(UUID.fromString(portfolioId));
            log.info("PortfolioController - getPortfolioById - Portfolio found: {}", portfolio != null ? "yes" : "no");
            return ResponseEntity.ok(portfolio);
        } catch (IllegalArgumentException e) {
            log.error("PortfolioController - getPortfolioById - Invalid portfolio ID: {}", portfolioId, e);
            return ResponseEntity.badRequest().build();
        }
    }

    @Operation(summary = "Get all portfolios for user", description = "Retrieves all portfolios associated with a specific user ID")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "List of portfolios retrieved successfully"),
            @ApiResponse(responseCode = "404", description = "No portfolios found for user")
    })
    @GetMapping
    public ResponseEntity<List<PortfolioModelV1>> getPortfolios() {
        String userId = com.am.security.context.UserContext.getUserIdOrThrow();
        log.info("PortfolioController - getPortfolios called with userId: {}", userId);

        List<PortfolioModelV1> portfolios = portfolioService.getPortfoliosByUserId(userId);
        log.info("PortfolioController - getPortfolios - Found {} portfolios for user: {}",
                portfolios != null ? portfolios.size() : 0, userId);

        return ResponseEntity.ok(portfolios);
    }

    @Operation(summary = "Get portfolio IDs and names", description = "Retrieves a lightweight list of portfolio IDs and names for all user portfolios. Empty list when the user has no portfolios.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Portfolio list retrieved successfully (may be empty)")
    })
    @GetMapping("/list")
    public ResponseEntity<List<PortfolioBasicInfo>> getPortfolioBasicDetails() {
        String userId = com.am.security.context.UserContext.getUserIdOrThrow();
        log.info("PortfolioController - getPortfolioBasicDetails called with userId: {}", userId);

        List<PortfolioModelV1> portfolios = portfolioService.getPortfoliosByUserId(userId);

        if (portfolios == null || portfolios.isEmpty()) {
            log.info("PortfolioController - getPortfolioBasicDetails - No portfolios for user: {} (returning empty list)",
                    userId);
            // Empty is a valid state — UI shows "No portfolios" / upload CTA. Do not 404.
            return ResponseEntity.ok(java.util.Collections.emptyList());
        }

        List<PortfolioBasicInfo> basicInfoList = portfolios.stream()
                .map(portfolio -> new PortfolioBasicInfo(
                        portfolio.getId() != null ? portfolio.getId().toString() : null,
                        portfolio.getName()))
                .collect(java.util.stream.Collectors.toList());

        log.info("PortfolioController - getPortfolioBasicDetails - Found {} portfolio basic details for user: {}",
                basicInfoList.size(), userId);

        return ResponseEntity.ok(basicInfoList);
    }




    @Operation(summary = "Trigger snapshot", description = "Manually triggers the end of day portfolio snapshot generation. Returns immediately — job runs in the background.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "202", description = "Snapshot job accepted and running in background")
    })
    @PostMapping("/trigger-snapshot")
    public ResponseEntity<String> triggerSnapshot() {
        log.info("PortfolioController - triggerSnapshot called manually via API");
        portfolioHistoryScheduler.runEndOfDayJobAsync();
        return ResponseEntity.accepted().body("Snapshot job accepted. Running in background — check server logs for progress.");
    }

    @Hidden
    @Operation(summary = "Get portfolio analysis", description = "Retrieves detailed analysis for a specific portfolio (hidden from API docs)")
    @GetMapping("/{portfolioId}/analysis")
    public ResponseEntity<PortfolioAnalysis> getPortfolioAnalysis(
            @PathVariable String portfolioId,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size,
            @RequestParam(required = false) String interval) {
        String userId = com.am.security.context.UserContext.getUserIdOrThrow();
        log.info(
                "PortfolioController - getPortfolioAnalysis called - Portfolio: {}, User: {}, Page: {}, Size: {}, Interval: {}",
                portfolioId, userId, page, size, interval != null ? interval : "null");

        try {
            TimeInterval timeInterval = TimeInterval.fromCode(interval);
            PortfolioAnalysis analysis = portfolioDashboardService.analyzePortfolio(
                    portfolioId, userId, page, size, timeInterval);

            if (analysis == null) {
                log.warn("PortfolioController - getPortfolioAnalysis - No analysis found for portfolio: {}",
                        portfolioId);
                return ResponseEntity.notFound().build();
            }

            log.info("PortfolioController - getPortfolioAnalysis - Successfully retrieved analysis for portfolio: {}",
                    portfolioId);
            return ResponseEntity.ok(analysis);
        } catch (IllegalArgumentException e) {
            log.error("PortfolioController - getPortfolioAnalysis - Invalid interval: {}", interval, e);
            return ResponseEntity.badRequest().build();
        }
    }

    @Operation(summary = "Get portfolio summary", description = "Retrieves a summary of all portfolios for a user with performance metrics. Optionally filter by specific portfolio ID.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Portfolio summary retrieved successfully"),
            @ApiResponse(responseCode = "404", description = "No portfolio summary found for user")
    })
    @GetMapping("/summary")
    public ResponseEntity<PortfolioSummaryV1> getPortfolioSummary(
            @Parameter(description = "Optional portfolio ID to filter results for specific portfolio") @RequestParam(required = false) String portfolioId,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size,
            @RequestParam(required = false) String interval) {
        String userId = com.am.security.context.UserContext.getUserIdOrThrow();
        log.info(
                "PortfolioController - getPortfolioSummary called - User: {}, Portfolio: {}, Page: {}, Size: {}, Interval: {}",
                userId, portfolioId != null ? portfolioId : "all", page, size, interval != null ? interval : "null");

        try {
            TimeInterval timeInterval = TimeInterval.fromCode(interval);
            PortfolioSummaryV1 portfolioSummary;

            if (portfolioId != null && !portfolioId.trim().isEmpty() && !portfolioId.equals(userId)) {
                // Filter by specific portfolio
                log.info("PortfolioController - getPortfolioSummary - Filtering by portfolio: {}", portfolioId);
                portfolioSummary = portfolioDashboardService.overviewPortfolio(userId, portfolioId, timeInterval);
            } else {
                // Get summary for all portfolios
                portfolioSummary = portfolioDashboardService.overviewPortfolio(userId, timeInterval);
            }

            if (portfolioSummary == null) {
                log.info("PortfolioController - getPortfolioSummary - No summary found for user: {} and portfolio: {}. Returning empty state.",
                        userId, portfolioId != null ? portfolioId : "all");
                return ResponseEntity.ok(PortfolioSummaryV1.empty());
            }

            log.info(
                    "PortfolioController - getPortfolioSummary - Successfully retrieved summary for user: {} and portfolio: {}",
                    userId, portfolioId != null ? portfolioId : "all");
            return ResponseEntity.ok(portfolioSummary);
        } catch (IllegalArgumentException e) {
            log.error("PortfolioController - getPortfolioSummary - Invalid interval: {}", interval, e);
            return ResponseEntity.badRequest().build();
        }
    }

    @Operation(summary = "Get portfolio holdings", description = "Retrieves all holdings across portfolios for a user with current values. Optionally filter by specific portfolio ID.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Portfolio holdings retrieved successfully"),
            @ApiResponse(responseCode = "404", description = "No holdings found for user")
    })
    @GetMapping("/holdings")
    public ResponseEntity<PortfolioHoldings> getPortfolioHoldings(
            @Parameter(description = "Optional portfolio ID to filter results for specific portfolio") @RequestParam(required = false) String portfolioId,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size,
            @RequestParam(required = false) String interval) {
        String userId = com.am.security.context.UserContext.getUserIdOrThrow();
        log.info(
                "PortfolioController - getPortfolioHoldings called - User: {}, Portfolio: {}, Page: {}, Size: {}, Interval: {}",
                userId, portfolioId != null ? portfolioId : "all", page, size, interval != null ? interval : "null");

        try {
            TimeInterval timeInterval = TimeInterval.fromCode(interval);
            PortfolioHoldings portfolioHoldings;

            if (portfolioId != null && !portfolioId.trim().isEmpty() && !portfolioId.equals(userId)) {
                // Filter by specific portfolio
                log.info("PortfolioController - getPortfolioHoldings - Filtering by portfolio: {}", portfolioId);
                portfolioHoldings = portfolioDashboardService.getPortfolioHoldings(userId, portfolioId, timeInterval);
            } else {
                // Get holdings for all portfolios
                portfolioHoldings = portfolioDashboardService.getPortfolioHoldings(userId, timeInterval);
            }

            if (portfolioHoldings == null) {
                log.info(
                        "PortfolioController - getPortfolioHoldings - No holdings found for user: {} and portfolio: {}. Returning empty state.",
                        userId, portfolioId != null ? portfolioId : "all");
                return ResponseEntity.ok(PortfolioHoldings.empty());
            }

            log.info(
                    "PortfolioController - getPortfolioHoldings - Successfully retrieved holdings for user: {} and portfolio: {}",
                    userId, portfolioId != null ? portfolioId : "all");
            return ResponseEntity.ok(portfolioHoldings);
        } catch (IllegalArgumentException e) {
            log.error("PortfolioController - getPortfolioHoldings - Invalid interval: {}", interval, e);
            return ResponseEntity.badRequest().build();
        }
    }

    @Operation(summary = "Get portfolio history", description = "Retrieves the snapshot history of all portfolios for a user with the specified timeframe.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Portfolio history retrieved successfully"),
            @ApiResponse(responseCode = "404", description = "No history found for user")
    })
    @GetMapping("/history")
    public ResponseEntity<List<PortfolioSnapshotModel>> getPortfolioHistory(
            @RequestParam(required = false, defaultValue = "1M") String timeFrame) {
        String userId = com.am.security.context.UserContext.getUserIdOrThrow();
        log.info("PortfolioController - getPortfolioHistory called - User: {}, TimeFrame: {}", userId, timeFrame);

        List<PortfolioSnapshotModel> history = portfolioSnapshotService.getHistory(userId, null, timeFrame);
        if (history == null) {
            return ResponseEntity.ok(java.util.Collections.emptyList());
        }
        return ResponseEntity.ok(history);
    }

    @Operation(summary = "Get specific portfolio history", description = "Retrieves the snapshot history of a specific portfolio for a user with the specified timeframe.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Portfolio history retrieved successfully"),
            @ApiResponse(responseCode = "404", description = "No history found for portfolio")
    })
    @GetMapping("/{portfolioId}/history")
    public ResponseEntity<List<PortfolioSnapshotModel>> getSpecificPortfolioHistory(
            @PathVariable String portfolioId,
            @RequestParam(required = false, defaultValue = "1M") String timeFrame) {
        String userId = com.am.security.context.UserContext.getUserIdOrThrow();
        log.info("PortfolioController - getSpecificPortfolioHistory called - User: {}, Portfolio: {}, TimeFrame: {}", userId, portfolioId, timeFrame);

        List<PortfolioSnapshotModel> history = portfolioSnapshotService.getHistory(userId, portfolioId, timeFrame);
        if (history == null) {
            return ResponseEntity.ok(java.util.Collections.emptyList());
        }
        return ResponseEntity.ok(history);
    }

    /**
     * DEV/ADMIN ONLY — Hidden from Swagger.
     * Directly triggers historical snapshot catch-up for any given userId.
     * Usage: POST /v1/portfolios/dev/trigger-catchup?userId=sahim99
     */
    @Hidden
    @PostMapping("/dev/trigger-catchup")
    public ResponseEntity<String> triggerCatchUpForUser(
            @RequestParam String userId,
            @RequestHeader(value = "X-Internal-Secret", required = false) String secret) {
        if (secret == null || !internalSecret.equals(secret)) {
            return ResponseEntity.status(403).body("Forbidden");
        }
        log.info("[DEV] Manual catch-up trigger for userId={}", userId);
        snapshotCatchUpService.triggerCatchUp(userId);
        return ResponseEntity.ok("CatchUp triggered for userId=" + userId + ". Check server logs for progress.");
    }

    /**
     * DEV/ADMIN ONLY — Hidden from Swagger.
     * Triggers manual EOD snapshot for today or a specific date.
     * Usage: POST /v1/portfolios/dev/trigger-snapshot?userId=sahim99&date=2026-07-28
     */
    @Hidden
    @PostMapping("/dev/trigger-snapshot")
    public ResponseEntity<String> triggerSnapshotForUser(
            @RequestParam String userId,
            @RequestParam(required = false) String date,
            @RequestHeader(value = "X-Internal-Secret", required = false) String secret) {
        if (secret == null || !internalSecret.equals(secret)) {
            return ResponseEntity.status(403).body("Forbidden");
        }
        LocalDate targetDate = date != null ? LocalDate.parse(date) : LocalDate.now();
        log.info("[DEV] Manual snapshot trigger for userId={} date={}", userId, targetDate);
        portfolioHistoryScheduler.runEndOfDayJobForUserAndDateAsync(userId, targetDate);
        return ResponseEntity.ok("Snapshot generation triggered for userId=" + userId + " date=" + targetDate);
    }

    /**
     * DEV/ADMIN ONLY — Hidden from Swagger.
     * Triggers snapshot backfilling for a range of past dates.
     * Usage: POST /v1/portfolios/dev/backfill-snapshots?userId=sahim99&days=30
     */
    @Hidden
    @PostMapping("/dev/backfill-snapshots")
    @Deprecated
    public ResponseEntity<String> backfillSnapshots(
            @RequestParam String userId,
            @RequestParam(defaultValue = "30") int days,
            @RequestHeader(value = "X-Internal-Secret", required = false) String secret) {
        if (secret == null || !internalSecret.equals(secret)) {
            return ResponseEntity.status(403).body("Forbidden");
        }
        log.warn("[DEV] DEPRECATED: /dev/backfill-snapshots called for userId={}. Please use /dev/trigger-catchup instead.", userId);
        portfolioHistoryScheduler.backfillSnapshotsAsync(userId, days);
        return ResponseEntity.ok("DEPRECATED: Use /dev/trigger-catchup. Backfill triggered for userId=" + userId + " for last " + days + " days.");
    }

    /**
     * DEV/ADMIN ONLY — Hidden from Swagger.
     * Safely deletes a snapshot for a specific user and date.
     * Usage: DELETE /v1/portfolios/dev/snapshot?userId=sahim99&date=2026-07-30
     */
    @Hidden
    @DeleteMapping("/dev/snapshot")
    public ResponseEntity<String> deleteSnapshot(
            @RequestParam String userId,
            @RequestParam @org.springframework.format.annotation.DateTimeFormat(iso = org.springframework.format.annotation.DateTimeFormat.ISO.DATE) LocalDate date,
            @RequestHeader(value = "X-Internal-Secret", required = false) String secret) {
        if (secret == null || !internalSecret.equals(secret)) {
            return ResponseEntity.status(403).body("Forbidden");
        }
        log.info("[DEV] Delete snapshot trigger for userId={} date={}", userId, date);
        portfolioSnapshotService.deleteSnapshot(userId, date);
        return ResponseEntity.ok("Snapshot for userId=" + userId + " on date=" + date + " has been successfully deleted. You can now use the trigger-catchup endpoint to rebuild it.");
    }

    /**
     * DEV/ADMIN ONLY — Hidden from Swagger.
     * Fixes broken "Grow" or "Auto-created GROW" portfolio names in MongoDB.
     */
    @Hidden
    @PostMapping("/dev/migrate-groww")
    public ResponseEntity<String> migrateGrowwNames(
            @org.springframework.beans.factory.annotation.Autowired com.am.common.amcommondata.repository.portfolio.PortfolioDocumentRepository repo,
            @RequestHeader(value = "X-Internal-Secret", required = false) String secret) {
        if (secret == null || !internalSecret.equals(secret)) {
            return ResponseEntity.status(403).body("Forbidden");
        }
        log.info("[DEV] Running Groww name migration...");
        java.util.List<com.am.common.amcommondata.document.portfolio.PortfolioDocument> allDocs = repo.findAll();
        int updated = 0;
        for (com.am.common.amcommondata.document.portfolio.PortfolioDocument doc : allDocs) {
            if ("Grow".equals(doc.getName()) || "Auto-created GROW".equals(doc.getName())) {
                doc.setName("Groww");
                repo.save(doc);
                updated++;
                log.info("Migrated portfolio ID {} to name 'Groww'", doc.getId());
            }
        }
        return ResponseEntity.ok("Migration complete. Updated " + updated + " portfolios.");
    }
}

// Trigger workflow
