package com.portfolio.analytics.intelligence;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

/**
 * In-memory holdings + derived metrics for intelligence engines. No Mongo writes.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PortfolioIntelligenceSnapshot {

    private String portfolioId;

    @Builder.Default
    private List<Holding> holdings = new ArrayList<>();

    private double totalValue;
    private int holdingsCount;
    private int distinctSectors;
    private double top1Pct;
    private double maxSectorPct;
    private String maxSectorName;
    private double liquidSharePct;

    /** Trading-day history length used for vol/beta; 0 = omit those components. */
    @Builder.Default
    private int historyPoints = 0;

    private Double portRetPct;
    private Double niftyRetPct;
    private Double dailyVolPct;
    private Double beta;

    /** Optional daily portfolio returns (fraction). */
    private List<Double> portfolioDailyReturns;
    /** Optional daily NIFTY returns (fraction), same length as portfolioDailyReturns. */
    private List<Double> niftyDailyReturns;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Holding {
        private String symbol;
        private double value;
        private double weightPct;
        private String sector;
        private String industry;
        private String marketCap;
        /** EQUITY / FIXED_INCOME / COMMODITY / CASH / MUTUAL_FUND — default EQUITY when null. */
        private String assetClass;
    }

    public PortfolioIntelligenceSnapshot deepCopy() {
        List<Holding> copyHoldings = new ArrayList<>();
        if (holdings != null) {
            for (Holding h : holdings) {
                copyHoldings.add(Holding.builder()
                        .symbol(h.getSymbol())
                        .value(h.getValue())
                        .weightPct(h.getWeightPct())
                        .sector(h.getSector())
                        .industry(h.getIndustry())
                        .marketCap(h.getMarketCap())
                        .assetClass(h.getAssetClass())
                        .build());
            }
        }
        return PortfolioIntelligenceSnapshot.builder()
                .portfolioId(portfolioId)
                .holdings(copyHoldings)
                .totalValue(totalValue)
                .holdingsCount(holdingsCount)
                .distinctSectors(distinctSectors)
                .top1Pct(top1Pct)
                .maxSectorPct(maxSectorPct)
                .maxSectorName(maxSectorName)
                .liquidSharePct(liquidSharePct)
                .historyPoints(historyPoints)
                .portRetPct(portRetPct)
                .niftyRetPct(niftyRetPct)
                .dailyVolPct(dailyVolPct)
                .beta(beta)
                .portfolioDailyReturns(portfolioDailyReturns == null ? null : new ArrayList<>(portfolioDailyReturns))
                .niftyDailyReturns(niftyDailyReturns == null ? null : new ArrayList<>(niftyDailyReturns))
                .build();
    }
}
