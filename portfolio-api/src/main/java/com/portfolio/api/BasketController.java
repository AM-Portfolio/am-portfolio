package com.portfolio.api;

import com.portfolio.basket.model.BasketOpportunity;
import com.portfolio.basket.service.BasketAllocationService;
import com.portfolio.basket.service.BasketCatalogService;
import com.portfolio.basket.service.BasketEngineFacade;
import com.portfolio.basket.service.BasketEngineService;
import com.portfolio.basket.service.HoldingSectorEnricher;
import com.portfolio.model.portfolio.EquityHoldings;
import com.portfolio.service.basket.AllocationLedgerService;
import com.portfolio.service.basket.BasketPortfolioCreateService;
import com.portfolio.service.basket.BasketReadService;
import com.portfolio.service.basket.BasketDraftService;
import com.portfolio.service.basket.dto.BasketDetailDto;
import com.portfolio.service.basket.dto.BasketSummaryDto;
import com.portfolio.service.basket.dto.BasketDraftDtos;
import com.portfolio.service.portfolio.PortfolioHoldingsService;
import com.am.common.amcommondata.model.PortfolioModelV1;
import com.am.common.amcommondata.model.enums.PortfolioKind;
import com.am.common.amcommondata.service.PortfolioService;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import io.swagger.v3.oas.annotations.Operation;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/v1/basket")
@RequiredArgsConstructor
@Slf4j
public class BasketController {

    private final BasketEngineService basketService;
    private final BasketEngineFacade basketEngineFacade;
    private final BasketAllocationService basketAllocationService;
    private final BasketCatalogService basketCatalogService;
    private final PortfolioHoldingsService portfolioHoldingsService;
    private final HoldingSectorEnricher holdingSectorEnricher;
    private final BasketPortfolioCreateService basketPortfolioCreateService;
    private final PortfolioService portfolioService;
    private final AllocationLedgerService allocationLedgerService;
    private final BasketReadService basketReadService;
    private final BasketDraftService basketDraftService;

    @Operation(summary = "Get basket catalog", description = "Returns curated basket themes and default ETF query", operationId = "getBasketCatalog")
    @GetMapping("/catalog")
    public com.portfolio.basket.model.BasketCatalogResponse getCatalog() {
        return basketCatalogService.getCatalog();
    }

    @Operation(summary = "Upsert basket catalog", description = "Replace basket catalog themes (admin/ops)", operationId = "upsertBasketCatalog")
    @PutMapping("/catalog")
    public com.portfolio.basket.model.BasketCatalogResponse upsertCatalog(
            @RequestBody com.portfolio.model.basket.cache.CachedBasketCatalog body) {
        return basketCatalogService.upsertCatalog(body);
    }

    @Operation(summary = "Get /my", description = "Endpoint to getMyBaskets", operationId = "getMyBaskets")
    @GetMapping("/my")
    public List<BasketSummaryDto> getMyBaskets(
            @RequestParam String userId, @RequestParam(required = false) String portfolioId) {
        return basketReadService.findBasketsByOwner(userId, portfolioId);
    }

    @Operation(summary = "Post /opportunities", description = "Endpoint to getOpportunities", operationId = "getOpportunities")
    @PostMapping("/opportunities")
    public List<BasketOpportunity> getOpportunities(@RequestBody OpportunityRequest request) {
        log.info("Received Basket Opportunities Request - User: {}, Portfolio: {}, Query: {}",
                request.getUserId(), request.getPortfolioId(), request.getEtfQuery());

        List<EquityHoldings> userHoldings = resolveUserHoldings(request.getUserId(),
                request.getPortfolioId(), request.getUserHoldings());

        log.info("Generating opportunities for {} holdings", userHoldings.size());
        return basketEngineFacade.findOpportunities(userHoldings, request.getEtfQuery());
    }

    @Operation(summary = "Post /exposure", description = "Endpoint to endpoint", operationId = "endpoint")
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

    @Operation(summary = "Post /allocations", description = "Endpoint to endpoint", operationId = "endpoint")
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

    @Operation(summary = "Post /preview", description = "Endpoint to getPreview", operationId = "getPreview")
    @PostMapping("/preview")
    public BasketOpportunity getPreview(@RequestBody PreviewRequest request) {
        log.info("Received Basket Preview Request - ETF: {}, User: {}, Portfolio: {}",
                request.getEtfIsin(), request.getUserId(), request.getPortfolioId());

        List<EquityHoldings> userHoldings = resolveUserHoldings(request.getUserId(),
                request.getPortfolioId(), request.getUserHoldings());

        log.info("Fetch User Holdings complete. Count: {}", userHoldings.size());

        try {
            BasketOpportunity opportunity = basketEngineFacade.getPreview(request.getEtfIsin(), userHoldings);
            log.info("Basket Preview generated successfully for ETF: {}", request.getEtfIsin());
            return opportunity;
        } catch (Exception e) {
            log.error("Error generating Basket Preview for ETF: " + request.getEtfIsin(), e);
            throw e;
        }
    }

    @Operation(summary = "Post /apply-substitutes", description = "Endpoint to applySubstitutes", operationId = "applySubstitutes")
    @PostMapping("/apply-substitutes")
    public BasketOpportunity applySubstitutes(@RequestBody ApplySubstitutesRequest request) {
        if (request == null || request.getEtfIsin() == null || request.getEtfIsin().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "etfIsin is required");
        }
        List<EquityHoldings> userHoldings = resolveUserHoldings(request.getUserId(),
                request.getPortfolioId(), request.getUserHoldings());
        try {
            List<com.portfolio.basket.model.SubstituteAssignment> assignments = request.getAssignments() == null
                    ? java.util.Collections.emptyList()
                    : request.getAssignments().stream()
                            .map(a -> new com.portfolio.basket.model.SubstituteAssignment(
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
                base = basketEngineFacade.getPreview(request.getEtfIsin(), userHoldings);
            }

            return basketService.applySubstitutesOnExisting(base, userHoldings, assignments);
        } catch (IllegalStateException conflict) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, conflict.getMessage());
        }
    }

    @Operation(summary = "Create basket portfolio", description = "Create a basket portfolio from preview lines", operationId = "createBasketPortfolio")
    @PostMapping("/create-portfolio")
    public BasketPortfolioCreateService.CreateBasketResponse createPortfolio(
            @RequestBody BasketPortfolioCreateService.CreateBasketRequest request) {
        return basketPortfolioCreateService.create(request);
    }

    @Operation(summary = "List basket drafts", description = "List durable basket drafts for a user", operationId = "listBasketDrafts")
    @GetMapping("/drafts")
    public BasketDraftDtos.BasketDraftListResponse listDrafts(
            @RequestParam String userId,
            @RequestParam(required = false) String portfolioId) {
        return basketDraftService.listDrafts(userId, portfolioId);
    }

    @Operation(summary = "Upsert basket draft", description = "Save or update a basket draft (max 5 per user)", operationId = "upsertBasketDraft")
    @PutMapping("/drafts")
    public BasketDraftDtos.BasketDraftDetailDto upsertDraft(
            @RequestBody BasketDraftDtos.UpsertBasketDraftRequest request) {
        return basketDraftService.upsert(request);
    }

    @Operation(summary = "Upsert basket draft (POST)", description = "Same as PUT /drafts — gateway-friendly upsert", operationId = "upsertBasketDraftPost")
    @PostMapping("/drafts")
    public BasketDraftDtos.BasketDraftDetailDto upsertDraftPost(
            @RequestBody BasketDraftDtos.UpsertBasketDraftRequest request) {
        return basketDraftService.upsert(request);
    }

    @Operation(summary = "Get basket draft", description = "Load a full basket draft snapshot", operationId = "getBasketDraft")
    @GetMapping("/drafts/{draftId}")
    public BasketDraftDtos.BasketDraftDetailDto getDraft(
            @PathVariable String draftId,
            @RequestParam String userId) {
        return basketDraftService.getDraft(draftId, userId);
    }

    @Operation(summary = "Delete basket draft", description = "Hard-delete a basket draft", operationId = "deleteBasketDraft")
    @DeleteMapping("/drafts/{draftId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteDraft(
            @PathVariable String draftId,
            @RequestParam String userId) {
        basketDraftService.deleteDraft(draftId, userId);
    }

    @Operation(summary = "Get /{basketId}", description = "Endpoint to getBasketDetail", operationId = "getBasketDetail")
    @GetMapping("/{basketId}")
    public BasketDetailDto getBasketDetail(@PathVariable String basketId, @RequestParam String userId) {
        requireValidBasketId(basketId);
        return basketReadService.getBasketDetail(basketId, userId);
    }

    @Operation(summary = "Delete /{basketId}", description = "Endpoint to deleteBasket", operationId = "deleteBasket")
    @DeleteMapping("/{basketId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteBasket(@PathVariable String basketId, @RequestParam(required = false) String userId) {
        requireValidBasketId(basketId);
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

        String sourcePortfolioId = basket.getSourcePortfolioId();
        try {
            basketPortfolioCreateService.evictBasketCaches(userId, sourcePortfolioId, basketId);
        } catch (Exception e) {
            log.warn("Cache eviction failed after basket delete", e);
        }
    }

    @Operation(summary = "Post /calculate-quantities", description = "Endpoint to calculateQuantities", operationId = "calculateQuantities")
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

    @Operation(summary = "Post /calculate-quantities/final-preview", description = "Endpoint to calculate quantities for final preview", operationId = "calculateQuantitiesFinalPreview")
    @PostMapping("/calculate-quantities/final-preview")
    public BasketOpportunity calculateQuantitiesFinalPreview(@RequestBody CalculationRequest request) {
        return calculateQuantities(request);
    }

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class CalculationRequest {
        private Double investmentAmount;
        private Boolean includeHeld;
        private BasketOpportunity opportunity;
        private List<String> excludedSymbols;
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

        holdings = holdings.stream()
                .filter(h -> h.getAvailableQuantity() == null || h.getAvailableQuantity() > 0)
                .collect(java.util.stream.Collectors.toList());
        return holdingSectorEnricher.enrich(holdings);
    }

    private void requireValidBasketId(String basketId) {
        try {
            UUID.fromString(basketId);
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid basketId");
        }
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
}
