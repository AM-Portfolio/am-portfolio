package com.portfolio.api;

import com.portfolio.basket.model.BasketOpportunity;
import com.portfolio.basket.service.BasketAllocationService;
import com.portfolio.basket.service.BasketCatalogService;
import com.portfolio.basket.service.BasketEngineService;
import com.portfolio.service.basket.BasketPortfolioCreateService;
import com.portfolio.basket.service.HoldingSectorEnricher;
import com.portfolio.model.portfolio.EquityHoldings;
import com.portfolio.service.portfolio.PortfolioHoldingsService;
import com.am.common.amcommondata.service.PortfolioService;
import com.portfolio.service.basket.AllocationLedgerService;
import com.am.common.amcommondata.model.enums.PortfolioKind;
import com.am.common.amcommondata.model.PortfolioModelV1;
import java.util.UUID;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.RequiredArgsConstructor;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import io.swagger.v3.oas.annotations.Operation;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@RestController
@RequestMapping("/v1/basket")
@RequiredArgsConstructor
@Slf4j
public class BasketController {

    private final BasketEngineService basketService;
    private final BasketAllocationService basketAllocationService;
    private final BasketCatalogService basketCatalogService;
    private final PortfolioHoldingsService portfolioHoldingsService;
    private final HoldingSectorEnricher holdingSectorEnricher;
    private final BasketPortfolioCreateService basketPortfolioCreateService;
    private final PortfolioService portfolioService;
    private final AllocationLedgerService allocationLedgerService;
    private final com.portfolio.marketdata.service.MarketDataService marketDataService;

    @Operation(summary="Get /catalog", description="Endpoint to endpoint", operationId="endpoint")
    @GetMapping("/catalog")
    public com.portfolio.basket.model.BasketCatalogResponse getCatalog() {
        return basketCatalogService.getCatalog();
    }

    @Operation(summary="Put /catalog", description="Endpoint to endpoint", operationId="endpoint")
    @PutMapping("/catalog")
    public com.portfolio.basket.model.BasketCatalogResponse upsertCatalog(
            @RequestBody com.portfolio.model.basket.cache.CachedBasketCatalog body) {
        return basketCatalogService.upsertCatalog(body);
    }

    @Operation(summary="Get /my", description="Endpoint to getMyBaskets", operationId="getMyBaskets")
    @GetMapping("/my")
    public List<BasketSummaryDto> getMyBaskets(@RequestParam String userId, @RequestParam(required = false) String portfolioId) {
        if (userId == null || userId.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "userId is required");
        }
        List<PortfolioModelV1> portfolios = portfolioService.getPortfoliosByUserId(userId);
        return portfolios.stream()
                .filter(p -> PortfolioKind.isBasket(p.getPortfolioKind()) || 
                            (p.getPortfolioKind() == null && p.getSourcePortfolioId() != null && !p.getSourcePortfolioId().isBlank()))
                .filter(p -> portfolioId == null || portfolioId.isBlank() || portfolioId.equals(p.getSourcePortfolioId()))
                .map(p -> BasketSummaryDto.builder()
                        .id(p.getId().toString())
                        .etfName(p.getEtfName() != null ? p.getEtfName() : (p.getName() != null ? p.getName() : "Basket"))
                        .etfIsin(p.getEtfIsin() != null ? p.getEtfIsin() : "")
                        .status(p.getStatus() != null ? p.getStatus() : "ACTIVE")
                        .assetCount(p.getEquityModels() != null ? p.getEquityModels().size() : 0)
                        .gapMissingCount(p.getGapMissingCount())
                        .totalValue(p.getTotalValue())
                        .investmentAmount(p.getInvestmentAmount())
                        .createdAt(p.getCreatedAt())
                        .build())
                .collect(java.util.stream.Collectors.toList());
    }

    @Operation(summary="Post /opportunities", description="Endpoint to getOpportunities", operationId="getOpportunities")
    @PostMapping("/opportunities")
    public List<BasketOpportunity> getOpportunities(@RequestBody OpportunityRequest request) {
        log.info("Received Basket Opportunities Request - User: {}, Portfolio: {}, Query: {}",
                request.getUserId(), request.getPortfolioId(), request.getEtfQuery());

        List<EquityHoldings> userHoldings = resolveUserHoldings(request.getUserId(),
                request.getPortfolioId(), request.getUserHoldings());

        log.info("Generating opportunities for {} holdings", userHoldings.size());
        return basketService.findOpportunities(userHoldings, request.getEtfQuery());
    }

    @Operation(summary="Post /exposure", description="Endpoint to endpoint", operationId="endpoint")
    @PostMapping("/exposure")
    public com.portfolio.model.basket.ExposureResponse getExposure(@RequestBody OpportunityRequest request) {
        log.info("DIAGNOSTIC: Entered getExposure - User: {}, Portfolio: {}",
                request.getUserId(), request.getPortfolioId());

        List<EquityHoldings> userHoldings = resolveUserHoldings(request.getUserId(),
                request.getPortfolioId(), request.getUserHoldings());

        log.info("Calculating cumulative exposure for {} holdings", userHoldings.size());

        com.portfolio.model.basket.ExposureResponse response = basketAllocationService
                .calculateCumulativeExposure(userHoldings);

        response.setUserId(request.getUserId());
        response.setPortfolioId(request.getPortfolioId());

        return response;
    }

    @Operation(summary="Post /allocations", description="Endpoint to endpoint", operationId="endpoint")
    @PostMapping("/allocations")
    public com.portfolio.model.basket.PortfolioAllocationResponse getAllocations(
            @RequestBody OpportunityRequest request) {
        log.info("Calculating portfolio allocations - User: {}, Portfolio: {}",
                request.getUserId(), request.getPortfolioId());

        List<EquityHoldings> userHoldings = resolveUserHoldings(request.getUserId(),
                request.getPortfolioId(), request.getUserHoldings());

        log.info("Generating allocations for {} holdings", userHoldings.size());

        com.portfolio.model.basket.PortfolioAllocationResponse allocation = basketAllocationService
                .calculatePortfolioAllocation(userHoldings);

        allocation.setUserId(request.getUserId());
        allocation.setPortfolioId(request.getPortfolioId());

        return allocation;
    }

    @Operation(summary="Post /preview", description="Endpoint to getPreview", operationId="getPreview")
    @PostMapping("/preview")
    public BasketOpportunity getPreview(@RequestBody PreviewRequest request) {
        log.info("Received Basket Preview Request - ETF: {}, User: {}, Portfolio: {}",
                request.getEtfIsin(), request.getUserId(), request.getPortfolioId());

        List<EquityHoldings> userHoldings = resolveUserHoldings(request.getUserId(),
                request.getPortfolioId(), request.getUserHoldings());

        log.info("Fetch User Holdings complete. Count: {}", userHoldings.size());

        try {
            BasketOpportunity opportunity = basketService.getPreview(request.getEtfIsin(), userHoldings);
            log.info("Basket Preview generated successfully for ETF: {}", request.getEtfIsin());
            return opportunity;
        } catch (Exception e) {
            log.error("Error generating Basket Preview for ETF: " + request.getEtfIsin(), e);
            throw e;
        }
    }

    @Operation(summary="Post /apply-substitutes", description="Endpoint to applySubstitutes", operationId="applySubstitutes")
    @PostMapping("/apply-substitutes")
    public BasketOpportunity applySubstitutes(@RequestBody ApplySubstitutesRequest request) {
        if (request == null || request.getEtfIsin() == null || request.getEtfIsin().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "etfIsin is required");
        }
        List<EquityHoldings> userHoldings = resolveUserHoldings(request.getUserId(),
                request.getPortfolioId(), request.getUserHoldings());
        try {
            List<BasketEngineService.SubstituteAssignment> assignments = request.getAssignments() == null
                    ? java.util.Collections.emptyList()
                    : request.getAssignments().stream()
                            .map(a -> new BasketEngineService.SubstituteAssignment(
                                    a.getMissingIsin(), a.getSubstituteIsin(), a.getAssignedWeight()))
                            .toList();
            
            BasketOpportunity base;
            if (request.getCurrentOpportunity() != null &&
                request.getCurrentOpportunity().getComposition() != null &&
                !request.getCurrentOpportunity().getComposition().isEmpty()) {
                log.info("apply-substitutes: using client-provided opportunity");
                base = request.getCurrentOpportunity();
            } else {
                log.warn("apply-substitutes: currentOpportunity not provided — falling back to getPreview for: {}",
                    request.getEtfIsin());
                base = basketService.getPreview(request.getEtfIsin(), userHoldings);
            }
                    
            return basketService.applySubstitutesOnExisting(base, userHoldings, assignments);
        } catch (IllegalStateException conflict) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, conflict.getMessage());
        }
    }

    @Operation(summary="Post /create-portfolio", description="Endpoint to endpoint", operationId="endpoint")
    @PostMapping("/create-portfolio")
    public BasketPortfolioCreateService.CreateBasketResponse createPortfolio(
            @RequestBody BasketPortfolioCreateService.CreateBasketRequest request) {
        return basketPortfolioCreateService.create(request);
    }

    @Operation(summary="Get /{basketId}", description="Endpoint to getBasketDetail", operationId="getBasketDetail")
    @GetMapping("/{basketId}")
    public BasketDetailDto getBasketDetail(@PathVariable String basketId, @RequestParam String userId) {
        if (userId == null || userId.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "userId is required");
        }
        PortfolioModelV1 basket = portfolioService.getPortfolioById(UUID.fromString(basketId));
        if (basket == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Basket not found");
        }
        if (!userId.equals(basket.getOwner())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Not owner of basket");
        }
        if (!PortfolioKind.isBasket(basket.getPortfolioKind()) && basket.getPortfolioKind() != PortfolioKind.DELETED) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Portfolio is not a basket");
        }

        List<BasketLineDetail> lines = new java.util.ArrayList<>();
        double totalInvested = 0;
        double totalCurrent = 0;
        int heldCount = 0;
        int missingCount = 0;
        int underfundedCount = 0;

        if (basket.getEquityModels() != null) {
            List<String> symbols = basket.getEquityModels().stream()
                .map(com.am.common.amcommondata.model.asset.equity.EquityModel::getSymbol)
                .filter(s -> s != null && !s.isBlank())
                .distinct()
                .toList();
            java.util.Map<String, Double> prices = marketDataService.getCurrentPrices(new java.util.ArrayList<>(symbols));

            for (com.am.common.amcommondata.model.asset.equity.EquityModel eq : basket.getEquityModels()) {
                double qty = eq.getQuantity() != null ? eq.getQuantity() : 0.0;
                double avgPrice = eq.getAvgBuyingPrice() != null ? eq.getAvgBuyingPrice() : 0.0;
                double displayCurrentPrice = 0.0;
                Double livePrice = prices.get(eq.getSymbol());
                if (livePrice != null && livePrice > 0) {
                    displayCurrentPrice = livePrice;
                }

                // Fallback for aggregate calculations so missing data doesn't skew total P&L
                double aggregateCurrentPrice = (displayCurrentPrice > 0) ? displayCurrentPrice : avgPrice;

                double invested = qty * avgPrice;
                double current = qty * aggregateCurrentPrice;
                double pnl = (displayCurrentPrice > 0) ? (current - invested) : 0.0;


                totalInvested += invested;
                totalCurrent += current;

                String status = eq.getStatus() != null ? eq.getStatus() : "HELD";
                if ("MISSING".equalsIgnoreCase(status) || "MISSING_GAP".equalsIgnoreCase(status)) {
                    missingCount++;
                } else {
                    heldCount++;
                }

                lines.add(BasketLineDetail.builder()
                        .symbol(eq.getSymbol())
                        .isin(eq.getIsin())
                        .sector(eq.getSector())
                        .status(status)
                        .quantity(qty)
                        .avgPrice(avgPrice)
                        .currentPrice(displayCurrentPrice)
                        .pnl(pnl)
                        .build());
            }
        }
        missingCount = basket.getGapMissingCount() != null ? basket.getGapMissingCount() : missingCount;

        double coveragePercent = (heldCount + missingCount) > 0
                ? ((double) heldCount / (heldCount + missingCount)) * 100.0 : 0.0;
        
        double totalPnl = totalCurrent - totalInvested;
        double pnlPct = totalInvested > 0 ? (totalPnl / totalInvested) * 100.0 : 0.0;

        return BasketDetailDto.builder()
                .id(basket.getId().toString())
                .name(basket.getName() != null ? basket.getName() : (basket.getEtfName() != null ? basket.getEtfName() : "Basket"))
                .etfName(basket.getEtfName() != null ? basket.getEtfName() : "")
                .etfIsin(basket.getEtfIsin() != null ? basket.getEtfIsin() : "")
                .status(basket.getStatus() != null ? basket.getStatus() : "ACTIVE")
                .createdAt(basket.getCreatedAt())
                .investmentAmount(basket.getInvestmentAmount())
                .totalInvestedValue(totalInvested)
                .totalCurrentValue(totalCurrent)
                .totalPnL(totalPnl)
                .pnlPercent(pnlPct)
                .totalItems(heldCount + missingCount)
                .heldCount(heldCount)
                .missingCount(missingCount)
                .underfundedCount(underfundedCount)
                .coveragePercent(coveragePercent)
                .lines(lines)
                .build();
    }


    @Operation(summary="Delete /{basketId}", description="Endpoint to deleteBasket", operationId="deleteBasket")
    @DeleteMapping("/{basketId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteBasket(@PathVariable String basketId, @RequestParam(required = false) String userId) {
        if (userId == null || userId.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "userId is required");
        }
        
        PortfolioModelV1 basket = portfolioService.getPortfolioById(UUID.fromString(basketId));
        if (basket == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Basket not found");
        }
        
        if (!userId.equals(basket.getOwner())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Not owner of basket");
        }
        
        if (!PortfolioKind.isBasket(basket.getPortfolioKind())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Portfolio is not a basket");
        }
        
        allocationLedgerService.releaseAllocations(basketId, userId, "BASKET_DELETED");
        
        basket.setPortfolioKind(PortfolioKind.DELETED);
        portfolioService.savePortfolioDocument(basket);
        
        try {
            // Since HoldingsRedisService isn't exposed directly here but via other services,
            // we can evict it indirectly or let the next fetch deal with it. The plan mentions evicting caches.
            // Ideally we'd call holdingsRedisService.evictPortfolioHoldings(userId, basketId);
            // We can also evict the source portfolio holdings. For now, since BasketPortfolioCreateService has it,
            // we might not have holdingsRedisService directly in BasketController. Let's just rely on the next fetch or add it if needed.
        } catch (Exception e) {
            log.warn("Cache eviction failed", e);
        }
    }

    @Operation(summary="Post /calculate-quantities", description="Endpoint to calculateQuantities", operationId="calculateQuantities")
    @PostMapping("/calculate-quantities")
    public BasketOpportunity calculateQuantities(@RequestBody CalculationRequest request) {
        if (request == null || request.getInvestmentAmount() == null || request.getInvestmentAmount() <= 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "investmentAmount is required and must be greater than 0");
        }
        BasketOpportunity opportunity = request.getOpportunity();
        if (opportunity == null || opportunity.getComposition() == null || opportunity.getComposition().isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "opportunity.composition is required. Call POST /v1/basket/preview first and pass the full "
                            + "BasketOpportunity response (etfIsin, composition with etfWeight per stock). "
                            + "Fields like etfSymbol/holdings are not used.");
        }
        log.info("Calculating quantities for investment amount: {}, excluded: {}",
                request.getInvestmentAmount(),
                request.getExcludedSymbols() != null ? request.getExcludedSymbols().size() : 0);
        boolean includeHeld = request.getIncludeHeld() != null ? request.getIncludeHeld() : true;
        List<String> excludedSymbols = request.getExcludedSymbols() != null
                ? request.getExcludedSymbols() : java.util.Collections.emptyList();
        return basketService.calculateBasketQuantities(
                request.getInvestmentAmount(), opportunity, includeHeld, excludedSymbols);
    }

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class CalculationRequest {
        private Double investmentAmount;
        private Boolean includeHeld;
        private BasketOpportunity opportunity;
        private List<String> excludedSymbols;  // NEW: symbols to exclude from this calculation
    }

    private List<EquityHoldings> resolveUserHoldings(String userId, String portfolioId,
            List<EquityHoldings> manualHoldings) {
        List<EquityHoldings> holdings;
        if (manualHoldings != null && !manualHoldings.isEmpty()) {
            log.info("Using manual holdings provided in request. Count: {}", manualHoldings.size());
            holdings = manualHoldings;
        } else if (userId == null || userId.isEmpty()) {
            log.warn("No userId or manual holdings provided in request.");
            return java.util.Collections.emptyList();
        } else {
            com.portfolio.model.portfolio.PortfolioHoldings portfolioHoldings;
            if (portfolioId != null && !portfolioId.isEmpty()) {
                portfolioHoldings = portfolioHoldingsService.getPortfolioHoldings(userId, portfolioId, null, true);
            } else {
                portfolioHoldings = portfolioHoldingsService.getPortfolioHoldings(userId, null, true);
            }

            if (portfolioHoldings != null && portfolioHoldings.getEquityHoldings() != null) {
                holdings = portfolioHoldings.getEquityHoldings();
            } else {
                holdings = java.util.Collections.emptyList();
            }
        }

        // Filter zero-available and enrich sectors
        holdings = holdings.stream()
                .filter(h -> h.getAvailableQuantity() == null || h.getAvailableQuantity() > 0)
                .collect(java.util.stream.Collectors.toList());
        return holdingSectorEnricher.enrich(holdings);
    }

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class PreviewRequest {
        private String etfIsin;
        private String userId;
        private String portfolioId;
        private List<EquityHoldings> userHoldings;
    }

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class OpportunityRequest {
        private String userId;
        private String portfolioId;
        private String etfQuery;
        private String etfIsin;
        private List<EquityHoldings> userHoldings;
    }

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class ApplySubstitutesRequest {
        private String userId;
        private String etfIsin;
        private String portfolioId;
        private List<EquityHoldings> userHoldings;
        private List<AssignmentDto> assignments;
        private BasketOpportunity currentOpportunity;
    }

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class AssignmentDto {
        private String missingIsin;
        private String substituteIsin;
        private Double assignedWeight;
    }

    @Data
    @lombok.Builder
    public static class BasketSummaryDto {
        private String id;
        private String etfName;
        private String etfIsin;
        private String status;
        private Integer assetCount;
        private Integer gapMissingCount;
        private Double totalValue;
        private Double investmentAmount;
        private java.time.LocalDateTime createdAt;
    }

    @Data
    @lombok.Builder
    public static class BasketDetailDto {
        private String id;
        private String name;
        private String etfName;
        private String etfIsin;
        private String status;
        private Double totalInvestedValue;
        private Double totalCurrentValue;
        private Double investmentAmount;
        private Double totalPnL;
        private Double pnlPercent;
        private Double coveragePercent;
        private Integer totalItems;
        private Integer heldCount;
        private Integer missingCount;
        private Integer underfundedCount;
        private java.time.LocalDateTime createdAt;
        private List<BasketLineDetail> lines;
    }

    @Data
    @lombok.Builder
    public static class BasketLineDetail {
        private String symbol;
        private String isin;
        private String sector;
        private String status;
        private Double quantity;
        private Double avgPrice;
        private Double currentPrice;
        private Double pnl;
        private Double etfWeight;
        private Double rebalancedWeight;
    }
}