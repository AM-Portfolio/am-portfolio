package com.portfolio.service.basket;

import com.am.common.amcommondata.model.PortfolioModelV1;
import com.am.common.amcommondata.model.asset.equity.EquityModel;
import com.am.common.amcommondata.model.enums.PortfolioKind;
import com.am.common.amcommondata.service.PortfolioService;
import com.portfolio.marketdata.service.MarketDataService;
import com.portfolio.model.market.MarketData;
import com.portfolio.service.basket.dto.BasketDetailDto;
import com.portfolio.service.basket.dto.BasketLineDetailDto;
import com.portfolio.service.basket.dto.BasketSummaryDto;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class BasketReadService {

    private static final long STALE_PRICE_HOURS = 6;

    private final PortfolioService portfolioService;
    private final MarketDataService marketDataService;

    public List<BasketSummaryDto> findBasketsByOwner(String userId, String portfolioId) {
        if (userId == null || userId.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "userId is required");
        }
        return portfolioService.getPortfoliosByUserId(userId).stream()
                .filter(p -> p.getPortfolioKind() != PortfolioKind.DELETED)
                .filter(p -> PortfolioKind.isBasket(p.getPortfolioKind())
                        || (p.getPortfolioKind() == null && p.getSourcePortfolioId() != null
                                && !p.getSourcePortfolioId().isBlank()))
                .filter(p -> portfolioId == null || portfolioId.isBlank()
                        || portfolioId.equals(p.getSourcePortfolioId()))
                .map(this::toSummary)
                .collect(Collectors.toList());
    }

    public BasketDetailDto getBasketDetail(String basketId, String userId) {
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
        return buildDetail(basket);
    }

    private BasketSummaryDto toSummary(PortfolioModelV1 p) {
        return BasketSummaryDto.builder()
                .id(p.getId().toString())
                .etfName(p.getEtfName() != null ? p.getEtfName() : (p.getName() != null ? p.getName() : "Basket"))
                .etfIsin(p.getEtfIsin() != null ? p.getEtfIsin() : "")
                .status(p.getStatus() != null ? p.getStatus() : "ACTIVE")
                .assetCount(p.getEquityModels() != null ? p.getEquityModels().size() : 0)
                .gapMissingCount(p.getGapMissingCount())
                .totalValue(p.getTotalValue())
                .investmentAmount(p.getInvestmentAmount())
                .createdAt(p.getCreatedAt())
                .build();
    }

    private BasketDetailDto buildDetail(PortfolioModelV1 basket) {
        List<BasketLineDetailDto> lines = new ArrayList<>();
        double totalInvested = 0;
        double totalCurrent = 0;
        int heldCount = 0;
        int missingCount = 0;

        if (basket.getEquityModels() != null) {
            List<String> symbols = basket.getEquityModels().stream()
                    .map(EquityModel::getSymbol)
                    .filter(s -> s != null && !s.isBlank())
                    .collect(Collectors.toList());

            Map<String, MarketData> marketData = symbols.isEmpty()
                    ? Collections.emptyMap()
                    : marketDataService.getMarketData(symbols);

            for (EquityModel eq : basket.getEquityModels()) {
                double qty = eq.getQuantity() != null ? eq.getQuantity() : 0.0;
                double avgPrice = eq.getAvgBuyingPrice() != null ? eq.getAvgBuyingPrice() : 0.0;
                double storedPrice = eq.getCurrentPrice() != null && eq.getCurrentPrice() > 0
                        ? eq.getCurrentPrice() : 0.0;

                MarketData md = eq.getSymbol() != null ? marketData.get(eq.getSymbol()) : null;
                Double livePrice = resolveLivePrice(md);
                boolean stalePrice = isStalePrice(md);

                double displayCurrentPrice = 0.0;
                boolean hasMarketPrice = false;
                if (livePrice != null && livePrice > 0) {
                    displayCurrentPrice = livePrice;
                    hasMarketPrice = true;
                } else if (storedPrice > 0) {
                    displayCurrentPrice = storedPrice;
                    hasMarketPrice = true;
                    stalePrice = true;
                }

                double valuationPrice = hasMarketPrice ? displayCurrentPrice : avgPrice;
                double invested = qty * avgPrice;
                double current = qty * valuationPrice;
                double pnl = hasMarketPrice ? (current - invested) : 0.0;

                totalInvested += invested;
                totalCurrent += current;

                String status = eq.getStatus() != null ? eq.getStatus() : "HELD";
                if ("MISSING".equalsIgnoreCase(status) || "MISSING_GAP".equalsIgnoreCase(status)) {
                    missingCount++;
                } else {
                    heldCount++;
                }

                String companyName = eq.getCompanyName();
                if (companyName == null || companyName.isBlank()) {
                    companyName = eq.getName();
                }

                lines.add(BasketLineDetailDto.builder()
                        .symbol(eq.getSymbol())
                        .isin(eq.getIsin())
                        .sector(eq.getSector())
                        .status(status)
                        .quantity(qty)
                        .avgPrice(avgPrice)
                        .currentPrice(displayCurrentPrice)
                        .pnl(pnl)
                        .companyName(companyName)
                        .etfWeight(eq.getEtfWeight())
                        .coversEtfSymbol(eq.getCoversEtfSymbol())
                        .stalePrice(stalePrice)
                        .build());
            }
        }

        missingCount = basket.getGapMissingCount() != null ? basket.getGapMissingCount() : missingCount;

        double coveragePercent;
        if (basket.getReplicaScore() != null && basket.getReplicaScore() > 0) {
            coveragePercent = basket.getReplicaScore();
        } else if (basket.getCoverageAfterCreation() != null && basket.getCoverageAfterCreation() > 0) {
            coveragePercent = basket.getCoverageAfterCreation();
        } else {
            coveragePercent = (heldCount + missingCount) > 0
                    ? ((double) heldCount / (heldCount + missingCount)) * 100.0 : 0.0;
        }

        double totalPnl = totalCurrent - totalInvested;
        double pnlPct = totalInvested > 0 ? (totalPnl / totalInvested) * 100.0 : 0.0;

        return BasketDetailDto.builder()
                .id(basket.getId().toString())
                .name(basket.getName() != null ? basket.getName()
                        : (basket.getEtfName() != null ? basket.getEtfName() : "Basket"))
                .etfName(basket.getEtfName() != null ? basket.getEtfName() : "")
                .etfIsin(basket.getEtfIsin() != null ? basket.getEtfIsin() : "")
                .status(basket.getStatus() != null ? basket.getStatus() : "ACTIVE")
                .createdAt(basket.getCreatedAt())
                .updatedAt(basket.getUpdatedAt())
                .investmentAmount(basket.getInvestmentAmount())
                .totalInvestedValue(totalInvested)
                .totalCurrentValue(totalCurrent)
                .totalPnL(totalPnl)
                .pnlPercent(pnlPct)
                .replicaScore(basket.getReplicaScore())
                .coverageAfterCreation(basket.getCoverageAfterCreation())
                .totalItems(heldCount + missingCount)
                .heldCount(heldCount)
                .missingCount(missingCount)
                .underfundedCount(0)
                .coveragePercent(coveragePercent)
                .lines(lines)
                .build();
    }

    private Double resolveLivePrice(MarketData md) {
        if (md == null) {
            return null;
        }
        if (md.getLastPrice() != null && md.getLastPrice() > 0) {
            return md.getLastPrice();
        }
        if (md.getPreviousClose() != null && md.getPreviousClose() > 0) {
            return md.getPreviousClose();
        }
        if (md.getOhlc() != null && md.getOhlc().getClose() > 0) {
            return md.getOhlc().getClose();
        }
        return null;
    }

    private boolean isStalePrice(MarketData md) {
        if (md == null || md.getTimestamp() == null) {
            return false;
        }
        return md.getTimestamp().isBefore(Instant.now().minus(STALE_PRICE_HOURS, ChronoUnit.HOURS));
    }
}
