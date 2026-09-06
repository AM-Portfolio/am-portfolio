package com.portfolio.service.basket;

import com.am.common.amcommondata.document.basket.BasketCreateIdempotencyDocument;
import com.am.common.amcommondata.model.HoldingAllocation;
import com.am.common.amcommondata.model.PortfolioModelV1;
import com.am.common.amcommondata.model.asset.equity.EquityModel;
import com.am.common.amcommondata.model.enums.PortfolioKind;
import com.am.common.amcommondata.repository.basket.BasketCreateIdempotencyRepository;
import com.am.common.amcommondata.service.PortfolioService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.portfolio.basket.util.BasketNaming;
import com.portfolio.redis.service.ActiveMarketSymbolPublisher;
import com.portfolio.redis.service.PortfolioHoldingsRedisService;
import com.portfolio.redis.service.PortfolioSummaryRedisService;
import com.portfolio.service.basket.AllocationLedgerService.AllocationLine;
import lombok.Builder;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

@Service
@Slf4j
@RequiredArgsConstructor
public class BasketPortfolioCreateService {

    private final PortfolioService portfolioService;
    private final ObjectMapper objectMapper;

    @Autowired(required = false)
    private BasketCreateIdempotencyRepository idempotencyRepository;

    @Autowired(required = false)
    private PortfolioHoldingsRedisService holdingsRedisService;

    @Autowired(required = false)
    private PortfolioSummaryRedisService summaryRedisService;

    @Autowired(required = false)
    private ActiveMarketSymbolPublisher activeMarketSymbolPublisher;

    @Autowired(required = false)
    private BasketDraftService basketDraftService;

    private final AllocationLedgerService allocationLedgerService;
    private final AllocationAvailabilityService allocationAvailabilityService;

    /** L1 in-process idempotency cache; Mongo is source of truth across pods. */
    private final ConcurrentHashMap<String, CreateBasketResponse> idempotencyCache = new ConcurrentHashMap<>();

    public CreateBasketResponse create(CreateBasketRequest request) {
        if (request == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Request required");
        }
        String idempotencyKey = normalizeIdempotencyKey(request.getIdempotencyKey());
        if (idempotencyKey != null) {
            CreateBasketResponse cached = lookupIdempotentResponse(idempotencyKey);
            if (cached != null) {
                clearDraftAfterSuccess(request);
                return cached;
            }
            ClaimResult claim = claimIdempotency(idempotencyKey, request.getUserId());
            if (claim == ClaimResult.COMPLETED) {
                CreateBasketResponse existing = lookupIdempotentResponse(idempotencyKey);
                if (existing != null) {
                    clearDraftAfterSuccess(request);
                    return existing;
                }
            }
            if (claim == ClaimResult.IN_PROGRESS_OTHER) {
                CreateBasketResponse waited = waitForCompleted(idempotencyKey);
                if (waited != null) {
                    clearDraftAfterSuccess(request);
                    return waited;
                }
                throw new ResponseStatusException(HttpStatus.CONFLICT,
                        "Basket create already in progress for this idempotency key");
            }
        }
        try {
            return createInternal(request, idempotencyKey);
        } catch (ResponseStatusException e) {
            if (idempotencyKey != null) {
                markIdempotencyFailed(idempotencyKey);
            }
            throw e;
        } catch (RuntimeException e) {
            if (idempotencyKey != null) {
                markIdempotencyFailed(idempotencyKey);
            }
            throw e;
        }
    }

    private CreateBasketResponse createInternal(CreateBasketRequest request, String idempotencyKey) {
        if (request.getUserId() == null || request.getSourcePortfolioId() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "userId and sourcePortfolioId required");
        }
        if (request.getLines() == null || request.getLines().isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "lines required");
        }

        PortfolioModelV1 source;
        try {
            source = portfolioService.getPortfolioById(UUID.fromString(request.getSourcePortfolioId()));
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid sourcePortfolioId");
        }
        if (source == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Source portfolio not found");
        }
        if (source.getOwner() == null || !source.getOwner().equals(request.getUserId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Not owner of source portfolio");
        }
        if (PortfolioKind.isBasket(source.getPortfolioKind())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Source must be a BROKER portfolio");
        }

        Map<String, EquityModel> equityByIsin = new HashMap<>();
        if (source.getEquityModels() != null) {
            for (EquityModel e : source.getEquityModels()) {
                if (e.getIsin() != null) {
                    equityByIsin.put(e.getIsin(), e);
                }
            }
        }

        Map<String, Double> activeAllocations =
                allocationAvailabilityService.getActiveAllocations(source.getId().toString());

        List<EquityModel> basketEquities = new ArrayList<>();
        List<HoldingAllocation> newAllocations = new ArrayList<>();
        List<MovedLine> moved = new ArrayList<>();
        List<AllocationLine> ledgerLines = new ArrayList<>();

        for (CreateBasketLine line : request.getLines()) {
            String status = line.getStatus();

            if ("MISSING".equalsIgnoreCase(status)) {
                if (line.getQuantity() == null || line.getQuantity() <= 0) continue;
                basketEquities.add(EquityModel.builder()
                        .symbol(line.getHoldingSymbol())
                        .isin(line.getHoldingIsin() != null ? line.getHoldingIsin() : line.getEtfIsin())
                        .quantity(line.getQuantity())
                        .avgBuyingPrice(line.getAverageBuyingPrice() != null ? line.getAverageBuyingPrice() : 0.0)
                        .status("MISSING")
                        .etfWeight(line.getEtfWeight())
                        .coversEtfSymbol(line.getEtfSymbol())
                        .build());
                continue;
            }

            if ("HELD".equalsIgnoreCase(status) || "SUBSTITUTE".equalsIgnoreCase(status)) {
                double heldQty = line.getHeldQuantity() != null ? line.getHeldQuantity() : 0.0;
                if (heldQty <= 0) continue;

                EquityModel sourceEq = equityByIsin.get(line.getHoldingIsin());
                if (sourceEq == null) {
                    throw new ResponseStatusException(HttpStatus.CONFLICT,
                            "Holding not on source: " + line.getHoldingIsin());
                }

                allocateFromSource(line, sourceEq, status, heldQty, activeAllocations, ledgerLines,
                        basketEquities, newAllocations, moved);
                continue;
            }

            if (line.getHoldingIsin() == null) {
                log.warn("Skipping line {} — holdingIsin is null", line.getHoldingSymbol());
                continue;
            }
            if (line.getQuantity() == null || line.getQuantity() <= 0) {
                log.warn("Skipping line {} — quantity is null or <= 0", line.getHoldingSymbol());
                continue;
            }
            EquityModel sourceEq = equityByIsin.get(line.getHoldingIsin());
            if (sourceEq == null) {
                throw new ResponseStatusException(HttpStatus.CONFLICT,
                        "Holding not on source: " + line.getHoldingIsin());
            }

            allocateFromSource(line, sourceEq, status, line.getQuantity(), activeAllocations, ledgerLines,
                    basketEquities, newAllocations, moved);
        }

        String basketName = request.getBasketName();
        if (basketName == null || basketName.isBlank()) {
            basketName = BasketNaming.defaultBasketName(request.getEtfName(), source.getName());
        }

        Double replicaScore = request.getReplicaScore() != null ? request.getReplicaScore()
                : request.getCoverageAfterCreation();

        // Pre-assign basket id so we can reserve ledger BEFORE persist (no orphan on 409).
        UUID basketUuid = UUID.randomUUID();
        String basketId = basketUuid.toString();

        PortfolioModelV1 basket = PortfolioModelV1.builder()
                .id(basketUuid)
                .owner(request.getUserId())
                .name(basketName)
                .brokerType(source.getBrokerType())
                .portfolioKind(PortfolioKind.BASKET)
                .status("ACTIVE")
                .sourcePortfolioId(source.getId().toString())
                .etfIsin(request.getEtfIsin())
                .etfName(request.getEtfName())
                .investmentAmount(request.getInvestmentAmount())
                .replicaScore(replicaScore)
                .coverageAfterCreation(replicaScore)
                .createdFromBasketAt(LocalDateTime.now())
                .gapMissingCount(request.getRemainingMissingCount())
                .equityModels(basketEquities)
                .currency(source.getCurrency() != null ? source.getCurrency() : "INR")
                .totalValue(basketEquities.stream()
                        .mapToDouble(e -> (e.getQuantity() != null ? e.getQuantity() : 0)
                                * (e.getCurrentPrice() != null ? e.getCurrentPrice()
                                : (e.getAvgBuyingPrice() != null ? e.getAvgBuyingPrice() : 0)))
                        .sum())
                .createdBy(request.getUserId())
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();

        // Validate ledger capacity BEFORE any side effects that leave orphans.
        if (!ledgerLines.isEmpty()) {
            for (AllocationLine line : ledgerLines) {
                EquityModel sourceEq = equityByIsin.get(line.getIsin());
                double rawQty = (sourceEq != null && sourceEq.getQuantity() != null) ? sourceEq.getQuantity() : 0.0;
                double alreadyActive = activeAllocations.getOrDefault(line.getIsin(), 0.0);
                double available = rawQty - alreadyActive;
                if (alreadyActive + line.getQuantity() > rawQty + 1e-9) {
                    throw new ResponseStatusException(HttpStatus.CONFLICT,
                        "Over-allocation for ISIN " + line.getIsin()
                                + ": available=" + available + " requested=" + line.getQuantity());
                }
            }
        }

        // claim → reserve → persist → complete
        if (!ledgerLines.isEmpty()) {
            allocationLedgerService.reserveAllocations(basketId, source.getId().toString(), ledgerLines);
        }

        PortfolioModelV1 savedBasket;
        try {
            savedBasket = portfolioService.createBasketPortfolio(basket);
        } catch (RuntimeException e) {
            if (!ledgerLines.isEmpty()) {
                try {
                    allocationLedgerService.releaseAllocations(basketId, request.getUserId(), "create_persist_failed");
                } catch (Exception releaseEx) {
                    log.warn("Ledger release after persist failure: {}", releaseEx.getMessage());
                }
            }
            throw e;
        }

        String savedId = savedBasket.getId() != null ? savedBasket.getId().toString() : basketId;

        evictCaches(request.getUserId(), source.getId().toString(), savedId);
        publishSymbols(basketEquities);

        Map<String, Double> availableAfter = new HashMap<>();
        for (MovedLine movedLine : moved) {
            EquityModel e = equityByIsin.get(movedLine.getIsin());
            if (e != null && e.getIsin() != null) {
                availableAfter.put(e.getIsin(),
                        portfolioService.getAvailableQuantity(
                                portfolioService.getPortfolioById(source.getId()),
                                e.getIsin(),
                                e.getQuantity()));
            }
        }

        CreateBasketResponse response = CreateBasketResponse.builder()
                .portfolioId(savedId)
                .name(savedBasket.getName())
                .sourcePortfolioId(source.getId().toString())
                .movedLines(moved)
                .remainingMissing(request.getRemainingMissing())
                .availableAfter(availableAfter)
                .build();

        if (idempotencyKey != null) {
            completeIdempotency(idempotencyKey, request.getUserId(), response);
        }
        clearDraftAfterSuccess(request);
        return response;
    }

    private void clearDraftAfterSuccess(CreateBasketRequest request) {
        if (basketDraftService == null || request == null) {
            return;
        }
        basketDraftService.deleteAfterCreate(
                request.getUserId(),
                request.getDraftId(),
                request.getSourcePortfolioId(),
                request.getEtfIsin());
    }

    private String normalizeIdempotencyKey(String idempotencyKey) {
        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            return null;
        }
        return idempotencyKey.trim();
    }

    private enum ClaimResult {
        CLAIMED,
        COMPLETED,
        IN_PROGRESS_OTHER
    }

    private ClaimResult claimIdempotency(String idempotencyKey, String userId) {
        if (idempotencyRepository == null) {
            return ClaimResult.CLAIMED;
        }
        try {
            idempotencyRepository.save(BasketCreateIdempotencyDocument.builder()
                    .idempotencyKey(idempotencyKey)
                    .userId(userId)
                    .status(BasketCreateIdempotencyDocument.STATUS_IN_PROGRESS)
                    .createdAt(LocalDateTime.now())
                    .build());
            return ClaimResult.CLAIMED;
        } catch (DuplicateKeyException e) {
            Optional<BasketCreateIdempotencyDocument> existing = idempotencyRepository.findById(idempotencyKey);
            if (existing.isPresent()) {
                BasketCreateIdempotencyDocument doc = existing.get();
                if (BasketCreateIdempotencyDocument.STATUS_COMPLETED.equals(doc.getStatus())
                        && doc.getResponseJson() != null
                        && !doc.getResponseJson().isBlank()) {
                    return ClaimResult.COMPLETED;
                }
                if (BasketCreateIdempotencyDocument.STATUS_FAILED.equals(doc.getStatus())) {
                    // Reclaim failed slot
                    doc.setStatus(BasketCreateIdempotencyDocument.STATUS_IN_PROGRESS);
                    doc.setResponseJson(null);
                    doc.setPortfolioId(null);
                    doc.setCreatedAt(LocalDateTime.now());
                    idempotencyRepository.save(doc);
                    return ClaimResult.CLAIMED;
                }
            }
            return ClaimResult.IN_PROGRESS_OTHER;
        }
    }

    private CreateBasketResponse waitForCompleted(String idempotencyKey) {
        for (int i = 0; i < 10; i++) {
            CreateBasketResponse response = lookupIdempotentResponse(idempotencyKey);
            if (response != null) {
                return response;
            }
            try {
                Thread.sleep(50L);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return null;
            }
        }
        return lookupIdempotentResponse(idempotencyKey);
    }

    private CreateBasketResponse lookupIdempotentResponse(String idempotencyKey) {
        CreateBasketResponse cached = idempotencyCache.get(idempotencyKey);
        if (cached != null) {
            return cached;
        }
        if (idempotencyRepository == null) {
            return null;
        }
        return idempotencyRepository.findById(idempotencyKey)
                .filter(doc -> BasketCreateIdempotencyDocument.STATUS_COMPLETED.equals(doc.getStatus())
                        || (doc.getStatus() == null && doc.getResponseJson() != null))
                .map(this::deserializeIdempotentResponse)
                .map(response -> {
                    if (response != null) {
                        idempotencyCache.put(idempotencyKey, response);
                    }
                    return response;
                })
                .orElse(null);
    }

    private void completeIdempotency(String idempotencyKey, String userId, CreateBasketResponse response) {
        idempotencyCache.put(idempotencyKey, response);
        if (idempotencyRepository == null) {
            return;
        }
        String json;
        try {
            json = objectMapper.writeValueAsString(response);
        } catch (JsonProcessingException e) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR,
                    "Failed to serialize create-portfolio idempotency response");
        }
        RuntimeException last = null;
        for (int attempt = 0; attempt < 3; attempt++) {
            try {
                BasketCreateIdempotencyDocument doc = idempotencyRepository.findById(idempotencyKey)
                        .orElse(BasketCreateIdempotencyDocument.builder()
                                .idempotencyKey(idempotencyKey)
                                .userId(userId)
                                .createdAt(LocalDateTime.now())
                                .build());
                doc.setUserId(userId);
                doc.setPortfolioId(response.getPortfolioId());
                doc.setResponseJson(json);
                doc.setStatus(BasketCreateIdempotencyDocument.STATUS_COMPLETED);
                if (doc.getCreatedAt() == null) {
                    doc.setCreatedAt(LocalDateTime.now());
                }
                idempotencyRepository.save(doc);
                return;
            } catch (Exception e) {
                last = e instanceof RuntimeException ? (RuntimeException) e : new RuntimeException(e);
                log.warn("Idempotency complete retry {} for key {}: {}", attempt + 1, idempotencyKey, e.getMessage());
            }
        }
        throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR,
                "Failed to store create-portfolio idempotency key after successful create", last);
    }

    private void markIdempotencyFailed(String idempotencyKey) {
        if (idempotencyRepository == null || idempotencyKey == null) {
            return;
        }
        try {
            idempotencyRepository.findById(idempotencyKey).ifPresent(doc -> {
                if (!BasketCreateIdempotencyDocument.STATUS_COMPLETED.equals(doc.getStatus())) {
                    doc.setStatus(BasketCreateIdempotencyDocument.STATUS_FAILED);
                    idempotencyRepository.save(doc);
                }
            });
        } catch (Exception e) {
            log.warn("Failed to mark idempotency FAILED for {}: {}", idempotencyKey, e.getMessage());
        }
    }

    private CreateBasketResponse deserializeIdempotentResponse(BasketCreateIdempotencyDocument document) {
        if (document.getResponseJson() == null || document.getResponseJson().isBlank()) {
            return null;
        }
        try {
            return objectMapper.readValue(document.getResponseJson(), CreateBasketResponse.class);
        } catch (JsonProcessingException e) {
            log.warn("Idempotency deserialize fail for key {}: {}",
                    document.getIdempotencyKey(), e.getMessage());
            return null;
        }
    }

    private void allocateFromSource(
            CreateBasketLine line,
            EquityModel sourceEq,
            String status,
            double requestedQty,
            Map<String, Double> activeAllocations,
            List<AllocationLine> ledgerLines,
            List<EquityModel> basketEquities,
            List<HoldingAllocation> newAllocations,
            List<MovedLine> moved) {
        double raw = sourceEq.getQuantity() != null ? sourceEq.getQuantity() : 0.0;
        double inFlightAllocated =
                allocationAvailabilityService.getInFlightAllocated(ledgerLines, line.getHoldingIsin());
        double available = allocationAvailabilityService.getAvailableQuantity(
                activeAllocations, line.getHoldingIsin(), raw, inFlightAllocated);
        double allocQty = Math.min(requestedQty, available);
        if (allocQty <= 0) {
            if (requestedQty > 0) {
                throw new ResponseStatusException(HttpStatus.CONFLICT,
                        "Insufficient available quantity for ISIN " + line.getHoldingIsin()
                                + ": available=" + available + " requested=" + requestedQty);
            }
            return;
        }
        if (allocQty + 1e-9 < requestedQty) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Insufficient available quantity for ISIN " + line.getHoldingIsin()
                            + ": available=" + available + " requested=" + requestedQty);
        }

        double avg = line.getAverageBuyingPrice() != null
                ? line.getAverageBuyingPrice()
                : (sourceEq.getAvgBuyingPrice() != null ? sourceEq.getAvgBuyingPrice() : 0.0);

        EquityModel basketEq = EquityModel.builder()
                .symbol(line.getHoldingSymbol() != null ? line.getHoldingSymbol() : sourceEq.getSymbol())
                .isin(line.getHoldingIsin())
                .quantity(allocQty)
                .avgBuyingPrice(avg)
                .currentPrice(resolveCurrentPrice(sourceEq, line))
                .sector(sourceEq.getSector())
                .companyName(resolveCompanyName(sourceEq, line))
                .name(resolveCompanyName(sourceEq, line))
                .status(status)
                .etfWeight(line.getEtfWeight())
                .coversEtfSymbol(line.getEtfSymbol())
                .build();
        basketEquities.add(basketEq);

        newAllocations.add(HoldingAllocation.builder()
                .basketPortfolioId("PENDING")
                .isin(line.getHoldingIsin())
                .symbol(basketEq.getSymbol())
                .quantity(allocQty)
                .build());

        ledgerLines.add(AllocationLine.builder()
                .isin(line.getHoldingIsin())
                .symbol(basketEq.getSymbol())
                .quantity(allocQty)
                .build());

        moved.add(MovedLine.builder()
                .isin(line.getHoldingIsin())
                .symbol(basketEq.getSymbol())
                .quantity(allocQty)
                .coversEtfSymbol(line.getEtfSymbol())
                .build());
    }

    public void evictBasketCaches(String userId, String sourceId, String basketId) {
        evictCaches(userId, sourceId, basketId);
    }

    private void evictCaches(String userId, String sourceId, String basketId) {
        try {
            if (holdingsRedisService != null) {
                holdingsRedisService.evictPortfolioHoldings(userId, null);
                holdingsRedisService.evictPortfolioHoldings(userId, sourceId);
                holdingsRedisService.evictPortfolioHoldings(userId, basketId);
            }
        } catch (Exception e) {
            log.warn("Holdings redis evict fail-open: {}", e.getMessage());
        }
        try {
            if (summaryRedisService != null) {
                summaryRedisService.evictPortfolioSummary(userId, sourceId);
                summaryRedisService.evictPortfolioSummary(userId, basketId);
            }
        } catch (Exception e) {
            log.warn("Summary redis evict fail-open: {}", e.getMessage());
        }
    }

    private Double resolveCurrentPrice(EquityModel sourceEq, CreateBasketLine line) {
        if (line.getLastKnownPrice() != null && line.getLastKnownPrice() > 0) {
            return line.getLastKnownPrice();
        }
        if (sourceEq.getCurrentPrice() != null && sourceEq.getCurrentPrice() > 0) {
            return sourceEq.getCurrentPrice();
        }
        return null;
    }

    private String resolveCompanyName(EquityModel sourceEq, CreateBasketLine line) {
        if (line.getCompanyName() != null && !line.getCompanyName().isBlank()) {
            return line.getCompanyName();
        }
        if (sourceEq.getCompanyName() != null && !sourceEq.getCompanyName().isBlank()) {
            return sourceEq.getCompanyName();
        }
        return sourceEq.getName();
    }

    private void publishSymbols(List<EquityModel> equities) {
        if (activeMarketSymbolPublisher == null || equities == null) {
            return;
        }
        try {
            List<String> symbols = equities.stream()
                    .map(EquityModel::getSymbol)
                    .filter(s -> s != null && !s.isBlank())
                    .distinct()
                    .collect(Collectors.toList());
            if (!symbols.isEmpty()) {
                activeMarketSymbolPublisher.publishSymbols(symbols);
            }
        } catch (Exception e) {
            log.warn("Active symbol publish fail-open: {}", e.getMessage());
        }
    }

    @Data
    @Builder
    @lombok.NoArgsConstructor
    @lombok.AllArgsConstructor
    public static class CreateBasketRequest {
        private String userId;
        private String sourcePortfolioId;
        private String etfIsin;
        private String etfName;
        private String basketName;
        private String idempotencyKey;
        private Integer remainingMissingCount;
        private List<String> remainingMissing;
        private Double investmentAmount;
        private Double replicaScore;
        private Double coverageAfterCreation;
        private String draftId;
        private List<CreateBasketLine> lines;
    }

    @Data
    @Builder
    @lombok.NoArgsConstructor
    @lombok.AllArgsConstructor
    public static class CreateBasketLine {
        private String status;
        private String etfIsin;
        private String etfSymbol;
        private String holdingIsin;
        private String holdingSymbol;
        private Double quantity;
        private Double heldQuantity;
        private Double averageBuyingPrice;
        private Double etfWeight;
        private Double lastKnownPrice;
        private String companyName;
    }

    @Data
    @Builder
    @lombok.NoArgsConstructor
    @lombok.AllArgsConstructor
    public static class CreateBasketResponse {
        private String portfolioId;
        private String name;
        private String sourcePortfolioId;
        private List<MovedLine> movedLines;
        private List<String> remainingMissing;
        private Map<String, Double> availableAfter;
    }

    @Data
    @Builder
    @lombok.NoArgsConstructor
    @lombok.AllArgsConstructor
    public static class MovedLine {
        private String isin;
        private String symbol;
        private Double quantity;
        private String coversEtfSymbol;
    }
}
