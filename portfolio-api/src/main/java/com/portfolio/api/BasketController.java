package com.portfolio.api;

import com.portfolio.basket.model.BasketOpportunity;
import com.portfolio.basket.service.BasketEngineService;
import com.portfolio.model.portfolio.EquityHoldings;
import com.portfolio.service.portfolio.PortfolioHoldingsService;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.RequiredArgsConstructor;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/basket")
@RequiredArgsConstructor
@Slf4j
public class BasketController {

    private final BasketEngineService basketService;
    private final PortfolioHoldingsService portfolioHoldingsService;

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

        com.portfolio.model.basket.ExposureResponse response = basketService.calculateCumulativeExposure(userHoldings);

        // Enrich response with request context
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

        com.portfolio.model.basket.PortfolioAllocationResponse allocation = basketService
                .calculatePortfolioAllocation(userHoldings);

        // Enrich with request context
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

    @PostMapping("/calculate-quantities")
    public BasketOpportunity calculateQuantities(@RequestBody CalculationRequest request) {
        log.info("Calculating quantities for investment amount: {}", request.getInvestmentAmount());
        return basketService.calculateBasketQuantities(request.getInvestmentAmount(), request.getOpportunity(), true);
    }

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class CalculationRequest {
        private Double investmentAmount;
        private BasketOpportunity opportunity;
    }

    private List<EquityHoldings> resolveUserHoldings(String userId, String portfolioId,
            List<EquityHoldings> manualHoldings) {
        // If manual holdings are provided, use them directly
        if (manualHoldings != null && !manualHoldings.isEmpty()) {
            log.info("Using manual holdings provided in request. Count: {}", manualHoldings.size());
            return manualHoldings;
        }

        // If no userId, return empty
        if (userId == null || userId.isEmpty()) {
            log.warn("No userId or manual holdings provided in request.");
            return java.util.Collections.emptyList();
        }

        // Fetch from Portfolio Service
        com.portfolio.model.portfolio.PortfolioHoldings portfolioHoldings;
        if (portfolioId != null && !portfolioId.isEmpty()) {
            portfolioHoldings = portfolioHoldingsService.getPortfolioHoldings(userId, portfolioId, null, false);
        } else {
            portfolioHoldings = portfolioHoldingsService.getPortfolioHoldings(userId, null, false);
        }

        if (portfolioHoldings != null && portfolioHoldings.getEquityHoldings() != null) {
            return portfolioHoldings.getEquityHoldings();
        }

        return java.util.Collections.emptyList();
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
        private String etfIsin; // Added to handle requests with specific ETF ISINs
        private List<EquityHoldings> userHoldings;
    }
}
