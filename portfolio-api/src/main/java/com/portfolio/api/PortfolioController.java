package com.portfolio.api;

import com.am.common.amcommondata.model.PortfolioModelV1;
import com.am.common.amcommondata.service.PortfolioService;
import com.portfolio.api.model.PortfolioBasicInfo;
import com.portfolio.model.TimeInterval;
import com.portfolio.model.portfolio.PortfolioAnalysis;
import com.portfolio.model.portfolio.PortfolioHoldings;
import com.portfolio.model.portfolio.v1.PortfolioSummaryV1;
import com.portfolio.service.PortfolioDashboardService;

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

@RestController
@RequestMapping("/v1/portfolios")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Portfolio Management", description = "Endpoints for managing user portfolios")
public class PortfolioController {

    private final PortfolioDashboardService portfolioDashboardService;
    private final PortfolioService portfolioService;

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

    @Operation(summary = "Get portfolio IDs and names", description = "Retrieves a lightweight list of portfolio IDs and names for all user portfolios")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Portfolio list retrieved successfully"),
            @ApiResponse(responseCode = "404", description = "No portfolios found for user")
    })
    @GetMapping("/list")
    public ResponseEntity<List<PortfolioBasicInfo>> getPortfolioBasicDetails() {
        String userId = com.am.security.context.UserContext.getUserIdOrThrow();
        log.info("PortfolioController - getPortfolioBasicDetails called with userId: {}", userId);

        List<PortfolioModelV1> portfolios = portfolioService.getPortfoliosByUserId(userId);

        if (portfolios == null || portfolios.isEmpty()) {
            log.warn("PortfolioController - getPortfolioBasicDetails - No portfolios found for user: {}", userId);
            return ResponseEntity.notFound().build();
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

    @Operation(summary = "Get all combined portfolios", description = "Retrieves a merged portfolio of all brokers for the user")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Combined portfolio retrieved successfully"),
            @ApiResponse(responseCode = "404", description = "No portfolios found for user")
    })
    @GetMapping("/all-combined")
    public ResponseEntity<PortfolioModelV1> getAllCombined() {
        String userId = com.am.security.context.UserContext.getUserIdOrThrow();
        log.info("PortfolioController - getAllCombined called with userId: {}", userId);

        List<PortfolioModelV1> portfolios = portfolioService.getPortfoliosByUserId(userId);
        
        if (portfolios == null || portfolios.isEmpty()) {
            return ResponseEntity.notFound().build();
        }

        java.util.Map<String, com.am.common.amcommondata.model.asset.equity.EquityModel> mergedEquitiesMap = new java.util.HashMap<>();
        double combinedTotalValue = 0.0;
        
        for (PortfolioModelV1 p : portfolios) {
            combinedTotalValue += p.getTotalValue() != null ? p.getTotalValue() : 0.0;
            if (p.getEquityModels() != null) {
                for (com.am.common.amcommondata.model.asset.equity.EquityModel eq : p.getEquityModels()) {
                    String symbol = eq.getSymbol();
                    if (mergedEquitiesMap.containsKey(symbol)) {
                        com.am.common.amcommondata.model.asset.equity.EquityModel existing = mergedEquitiesMap.get(symbol);
                        
                        double existingCost = existing.getQuantity() != null && existing.getAvgBuyingPrice() != null ? existing.getQuantity() * existing.getAvgBuyingPrice() : 0;
                        double newCost = eq.getQuantity() != null && eq.getAvgBuyingPrice() != null ? eq.getQuantity() * eq.getAvgBuyingPrice() : 0;
                        double newTotalQty = (existing.getQuantity() != null ? existing.getQuantity() : 0) + (eq.getQuantity() != null ? eq.getQuantity() : 0);
                        
                        existing.setQuantity(newTotalQty);
                        existing.setAvgBuyingPrice(newTotalQty > 0 ? (existingCost + newCost) / newTotalQty : 0);
                        
                        double existingCurrentValue = existing.getCurrentValue() != null ? existing.getCurrentValue() : 0;
                        double newCurrentValue = eq.getCurrentValue() != null ? eq.getCurrentValue() : 0;
                        existing.setCurrentValue(existingCurrentValue + newCurrentValue);
                        
                        double existingInvestValue = existing.getInvestmentValue() != null ? existing.getInvestmentValue() : 0;
                        double newInvestValue = eq.getInvestmentValue() != null ? eq.getInvestmentValue() : 0;
                        existing.setInvestmentValue(existingInvestValue + newInvestValue);
                        
                        double existingPl = existing.getProfitLoss() != null ? existing.getProfitLoss() : 0;
                        double newPl = eq.getProfitLoss() != null ? eq.getProfitLoss() : 0;
                        existing.setProfitLoss(existingPl + newPl);
                        
                        if (existing.getInvestmentValue() != null && existing.getInvestmentValue() > 0 && existing.getProfitLoss() != null) {
                            existing.setProfitLossPercentage((existing.getProfitLoss() / existing.getInvestmentValue()) * 100);
                        }
                    } else {
                        // Clone the model just to be safe from modifying original DTO list in cache/DB contexts
                        com.am.common.amcommondata.model.asset.equity.EquityModel cloned = com.am.common.amcommondata.model.asset.equity.EquityModel.builder()
                            .id(eq.getId())
                            .symbol(eq.getSymbol())
                            .name(eq.getName())
                            .description(eq.getDescription())
                            .assetType(eq.getAssetType())
                            .quantity(eq.getQuantity())
                            .avgBuyingPrice(eq.getAvgBuyingPrice())
                            .currentPrice(eq.getCurrentPrice())
                            .currentValue(eq.getCurrentValue())
                            .investmentValue(eq.getInvestmentValue())
                            .brokerType(null) // Reset broker type for combined
                            .profitLoss(eq.getProfitLoss())
                            .profitLossPercentage(eq.getProfitLossPercentage())
                            .todayProfitLoss(eq.getTodayProfitLoss())
                            .todayProfitLossPercentage(eq.getTodayProfitLossPercentage())
                            .companyName(eq.getCompanyName())
                            .sector(eq.getSector())
                            .industry(eq.getIndustry())
                            .marketCap(eq.getMarketCap())
                            .exchange(eq.getExchange())
                            .build();
                        mergedEquitiesMap.put(symbol, cloned);
                    }
                }
            }
        }
        
        PortfolioModelV1 combined = PortfolioModelV1.builder()
            .id(UUID.randomUUID())
            .name("All Portfolios")
            .owner(userId)
            .equityModels(new java.util.ArrayList<>(mergedEquitiesMap.values()))
            .totalValue(combinedTotalValue)
            .assetCount(mergedEquitiesMap.size())
            .build();
            
        log.info("PortfolioController - getAllCombined - Successfully aggregated {} portfolios into {} equities for user: {}",
                portfolios.size(), combined.getAssetCount(), userId);
                
        return ResponseEntity.ok(combined);
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
                log.warn("PortfolioController - getPortfolioSummary - No summary found for user: {} and portfolio: {}",
                        userId, portfolioId != null ? portfolioId : "all");
                return ResponseEntity.notFound().build();
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
                log.warn(
                        "PortfolioController - getPortfolioHoldings - No holdings found for user: {} and portfolio: {}",
                        userId, portfolioId != null ? portfolioId : "all");
                return ResponseEntity.notFound().build();
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

    @GetMapping("/kafka-debug")
    public ResponseEntity<String> debugKafka() {
        try {
            Class.forName("org.springframework.kafka.core.KafkaTemplate");
            return ResponseEntity.ok("KafkaTemplate is ON the classpath!");
        } catch (ClassNotFoundException e) {
            return ResponseEntity.ok("KafkaTemplate is MISSING from classpath!");
        }
    }
}
