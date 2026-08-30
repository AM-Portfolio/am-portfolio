package com.portfolio.service.basket;

import com.am.common.amcommondata.model.HoldingAllocation;
import com.am.common.amcommondata.model.PortfolioModelV1;
import com.am.common.amcommondata.model.asset.equity.EquityModel;
import com.am.common.amcommondata.model.enums.PortfolioKind;
import com.am.common.amcommondata.service.PortfolioService;
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
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

@Service
@Slf4j
@RequiredArgsConstructor
public class BasketPortfolioCreateService {

    private final PortfolioService portfolioService;

    @Autowired(required = false)
    private PortfolioHoldingsRedisService holdingsRedisService;

    @Autowired(required = false)
    private PortfolioSummaryRedisService summaryRedisService;

    @Autowired(required = false)
    private ActiveMarketSymbolPublisher activeMarketSymbolPublisher;

    private final AllocationLedgerService allocationLedgerService;

    /** In-process idempotency (Redis optional). */
    private final ConcurrentHashMap<String, CreateBasketResponse> idempotencyCache = new ConcurrentHashMap<>();

    public CreateBasketResponse create(CreateBasketRequest request) {
        if (request == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Request required");
        }
        if (request.getIdempotencyKey() != null && !request.getIdempotencyKey().isBlank()) {
            CreateBasketResponse cached = idempotencyCache.get(request.getIdempotencyKey());
            if (cached != null) {
                return cached;
            }
        }
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

        List<EquityModel> basketEquities = new ArrayList<>();
        List<HoldingAllocation> newAllocations = new ArrayList<>();
        List<MovedLine> moved = new ArrayList<>();
        List<AllocationLine> ledgerLines = new ArrayList<>();

        for (CreateBasketLine line : request.getLines()) {
            String status = line.getStatus();

            // PATH 1: MISSING — persist to basket, no ledger entry
            if ("MISSING".equalsIgnoreCase(status)) {
                if (line.getQuantity() == null || line.getQuantity() <= 0) continue;
                basketEquities.add(EquityModel.builder()
                        .symbol(line.getHoldingSymbol())
                        .isin(line.getHoldingIsin() != null ? line.getHoldingIsin() : line.getEtfIsin())
                        .quantity(line.getQuantity())
                        .avgBuyingPrice(line.getAverageBuyingPrice() != null ? line.getAverageBuyingPrice() : 0.0)
                        .status("MISSING")
                        .build());
                continue; // NO ledger entry
            }

            // PATH 2: HELD or SUBSTITUTE — use heldQuantity, earmark from source portfolio
            if ("HELD".equalsIgnoreCase(status) || "SUBSTITUTE".equalsIgnoreCase(status)) {
                double heldQty = line.getHeldQuantity() != null ? line.getHeldQuantity() : 0.0;
                if (heldQty <= 0) continue;
                
                EquityModel sourceEq = equityByIsin.get(line.getHoldingIsin());
                if (sourceEq == null) {
                    throw new ResponseStatusException(HttpStatus.CONFLICT,
                            "Holding not on source: " + line.getHoldingIsin());
                }
                
                double raw = sourceEq.getQuantity() != null ? sourceEq.getQuantity() : 0.0;
                double dbAllocated = allocationLedgerService.sumActiveQuantityByBrokerPortfolioIdAndIsin(source.getId().toString(), line.getHoldingIsin());
                double inFlightAllocated = ledgerLines.stream()
                        .filter(a -> line.getHoldingIsin().equals(a.getIsin()))
                        .mapToDouble(a -> a.getQuantity() != null ? a.getQuantity() : 0)
                        .sum();
                double alreadyAllocated = dbAllocated + inFlightAllocated;
                double available = Math.max(0.0, raw - alreadyAllocated);
                double allocQty = Math.min(heldQty, available);
                if (allocQty <= 0) continue;

                double avg = line.getAverageBuyingPrice() != null
                        ? line.getAverageBuyingPrice()
                        : (sourceEq.getAvgBuyingPrice() != null ? sourceEq.getAvgBuyingPrice() : 0.0);

                EquityModel basketEq = EquityModel.builder()
                        .symbol(line.getHoldingSymbol() != null ? line.getHoldingSymbol() : sourceEq.getSymbol())
                        .isin(line.getHoldingIsin())
                        .quantity(allocQty)
                        .avgBuyingPrice(avg)
                        .currentPrice(sourceEq.getCurrentPrice())
                        .sector(sourceEq.getSector())
                        .companyName(sourceEq.getCompanyName())
                        .status(status)
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
                continue;
            }

            // PATH 3: Legacy/null status — existing buy logic (gap-fill)
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
            double raw = sourceEq.getQuantity() != null ? sourceEq.getQuantity() : 0.0;
            double dbAllocated = allocationLedgerService.sumActiveQuantityByBrokerPortfolioIdAndIsin(source.getId().toString(), line.getHoldingIsin());
            double inFlightAllocated = ledgerLines.stream()
                    .filter(a -> line.getHoldingIsin().equals(a.getIsin()))
                    .mapToDouble(a -> a.getQuantity() != null ? a.getQuantity() : 0)
                    .sum();
            double alreadyAllocated = dbAllocated + inFlightAllocated;
            double available = Math.max(0.0, raw - alreadyAllocated);
            double allocatedQty = Math.min(line.getQuantity(), available);
            if (allocatedQty < 1e-6) {
                log.warn("Skipping line {} — zero available (fully allocated to another basket)", line.getHoldingIsin());
                continue;
            }

            double avg = line.getAverageBuyingPrice() != null
                    ? line.getAverageBuyingPrice()
                    : (sourceEq.getAvgBuyingPrice() != null ? sourceEq.getAvgBuyingPrice() : 0.0);

            EquityModel basketEq = EquityModel.builder()
                    .symbol(line.getHoldingSymbol() != null ? line.getHoldingSymbol() : sourceEq.getSymbol())
                    .isin(line.getHoldingIsin())
                    .quantity(allocatedQty)
                    .avgBuyingPrice(avg)
                    .currentPrice(sourceEq.getCurrentPrice())
                    .sector(sourceEq.getSector())
                    .companyName(sourceEq.getCompanyName())
                    .build();
            basketEquities.add(basketEq);

            newAllocations.add(HoldingAllocation.builder()
                    .basketPortfolioId("PENDING")
                    .isin(line.getHoldingIsin())
                    .symbol(basketEq.getSymbol())
                    .quantity(allocatedQty)
                    .build());

            ledgerLines.add(AllocationLine.builder()
                    .isin(line.getHoldingIsin())
                    .symbol(basketEq.getSymbol())
                    .quantity(allocatedQty)
                    .build());

            moved.add(MovedLine.builder()
                    .isin(line.getHoldingIsin())
                    .symbol(basketEq.getSymbol())
                    .quantity(allocatedQty)
                    .coversEtfSymbol(line.getEtfSymbol())
                    .build());
        }

        String basketName = request.getBasketName();
        if (basketName == null || basketName.isBlank()) {
            basketName = BasketNaming.defaultBasketName(request.getEtfName(), source.getName());
        }

        PortfolioModelV1 basket = PortfolioModelV1.builder()
                .owner(request.getUserId())
                .name(basketName)
                .brokerType(source.getBrokerType())
                .portfolioKind(PortfolioKind.BASKET)
                .status("ACTIVE")
                .sourcePortfolioId(source.getId().toString())
                .etfIsin(request.getEtfIsin())
                .etfName(request.getEtfName())
                .investmentAmount(request.getInvestmentAmount())
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

        PortfolioModelV1 savedBasket = portfolioService.createBasketPortfolio(basket);
        String basketId = savedBasket.getId().toString();

        // Write ledger entries via compensating write pattern (PENDING -> ACTIVE)
        if (!ledgerLines.isEmpty()) {
            for (AllocationLine line : ledgerLines) {
                EquityModel sourceEq = equityByIsin.get(line.getIsin());
                double rawQty = (sourceEq != null && sourceEq.getQuantity() != null) ? sourceEq.getQuantity() : 0.0;
                double alreadyActive = allocationLedgerService.sumActiveQuantityByBrokerPortfolioIdAndIsin(
                        source.getId().toString(), line.getIsin());
                
                if (alreadyActive + line.getQuantity() > rawQty) {
                    throw new ResponseStatusException(HttpStatus.CONFLICT,
                        "Over-allocation for ISIN " + line.getIsin() +
                        ": available=" + (rawQty - alreadyActive) + " requested=" + line.getQuantity());
                }
            }
            allocationLedgerService.reserveAllocations(basketId, source.getId().toString(), ledgerLines);
        }

        evictCaches(request.getUserId(), source.getId().toString(), basketId);
        publishSymbols(basketEquities);

        Map<String, Double> availableAfter = new HashMap<>();
        for (EquityModel e : equityByIsin.values()) {
            if (e.getIsin() != null) {
                availableAfter.put(e.getIsin(),
                        portfolioService.getAvailableQuantity(
                                portfolioService.getPortfolioById(source.getId()),
                                e.getIsin(),
                                e.getQuantity()));
            }
        }

        CreateBasketResponse response = CreateBasketResponse.builder()
                .portfolioId(basketId)
                .name(savedBasket.getName())
                .sourcePortfolioId(source.getId().toString())
                .movedLines(moved)
                .remainingMissing(request.getRemainingMissing())
                .availableAfter(availableAfter)
                .build();

        if (request.getIdempotencyKey() != null && !request.getIdempotencyKey().isBlank()) {
            idempotencyCache.put(request.getIdempotencyKey(), response);
        }
        return response;
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
