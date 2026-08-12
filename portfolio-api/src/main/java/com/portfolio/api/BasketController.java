package com.portfolio.api;

import com.portfolio.basket.model.BasketOpportunity;
import com.portfolio.basket.service.BasketAllocationService;
import com.portfolio.basket.service.BasketCatalogService;
import com.portfolio.basket.service.BasketEngineService;
import com.portfolio.service.basket.BasketPortfolioCreateService;
import com.portfolio.basket.service.HoldingSectorEnricher;
import com.portfolio.model.portfolio.EquityHoldings;
import com.portfolio.service.portfolio.PortfolioHoldingsService;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.RequiredArgsConstructor;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
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

    @GetMapping("/catalog")
    public com.portfolio.basket.model.BasketCatalogResponse getCatalog() {
        return basketCatalogService.getCatalog();
    }

    @PutMapping("/catalog")
    public com.portfolio.basket.model.BasketCatalogResponse upsertCatalog(
            @RequestBody com.portfolio.model.basket.cache.CachedBasketCatalog body) {
        return basketCatalogService.upsertCatalog(body);
    }

    @PostMapping("/opportunities")
    public List<BasketOpportunity> getOpportunities(@RequestBody OpportunityRequest request) {
        log.info("Received Basket Opportunities Request - User: {}, Portfolio: {}, Query: {}",
                request.getUserId(), request.getPortfolioId(), request.getEtfQuery());

        List<EquityHoldings> userHoldings = resolveUserHoldings(request.getUserId(),
                request.getPortfolioId(), request.getUserHoldings());

        log.info("Generating opportunities for {} holdings", userHoldings.size());
        return basketService.findOpportunities(userHoldings, request.getEtfQuery());
    }

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

    @PostMapping("/apply-substitutes")
    public BasketOpportunity applySubstitutes(@RequestBody ApplySubstitutesRequest request) {
        if (request == null || request.getEtfIsin() == null || request.getEtfIsin().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "etfIsin is required");
        }
        List<EquityHoldings> userHoldings = resolveUserHoldings(request.getUserId(),
                request.getPortfolioId(), request.getUserHoldings());
        try {
            List<BasketEngineService.SubstituteAssignment> assignments = request.getAssignments() == null
                    ? List.of()
                    : request.getAssignments().stream()
                            .map(a -> new BasketEngineService.SubstituteAssignment(
                                    a.getMissingIsin(), a.getSubstituteIsin()))
                            .toList();
            
            BasketOpportunity base = request.getCurrentOpportunity() != null
                    ? request.getCurrentOpportunity()
                    : basketService.getPreview(request.getEtfIsin(), userHoldings);
                    
            return basketService.applySubstitutesOnExisting(base, userHoldings, assignments);
        } catch (IllegalStateException conflict) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, conflict.getMessage());
        }
    }

    @PostMapping("/create-portfolio")
    public BasketPortfolioCreateService.CreateBasketResponse createPortfolio(
            @RequestBody BasketPortfolioCreateService.CreateBasketRequest request) {
        return basketPortfolioCreateService.create(request);
    }

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
        log.info("Calculating quantities for investment amount: {}", request.getInvestmentAmount());
        return basketService.calculateBasketQuantities(request.getInvestmentAmount(), opportunity, true);
    }

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class CalculationRequest {
        private Double investmentAmount;
        private BasketOpportunity opportunity;
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
                portfolioHoldings = portfolioHoldingsService.getPortfolioHoldings(userId, portfolioId, null, false);
            } else {
                portfolioHoldings = portfolioHoldingsService.getPortfolioHoldings(userId, null, false);
            }

            if (portfolioHoldings != null && portfolioHoldings.getEquityHoldings() != null) {
                holdings = portfolioHoldings.getEquityHoldings();
            } else {
                holdings = java.util.Collections.emptyList();
            }
        }

        // Filter zero-available and enrich sectors
        holdings = holdings.stream()
                .filter(h -> h.getQuantity() == null || h.getQuantity() > 0)
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
    }
}
