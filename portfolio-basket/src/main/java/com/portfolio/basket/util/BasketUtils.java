package com.portfolio.basket.util;

import com.portfolio.model.portfolio.EquityHoldings;
import java.util.List;

public class BasketUtils {

    public static double round(double value) {
        return Math.round(value * 100.0) / 100.0;
    }

    /** Minimum 1 share when allocation warrants a non-zero target. */
    public static int resolveBaseTargetQty(double baseTargetAmount, double price, double weight) {
        if (price <= 0 || weight <= 0 || baseTargetAmount <= 0) {
            return 0;
        }
        int floored = (int) Math.floor(baseTargetAmount / price);
        return floored <= 0 ? 1 : floored;
    }

    /** Buy quantity from gap amount; min 1 when weight > 0 and gap is positive. */
    public static int resolveBuyQty(double gapAmount, double price, double weight) {
        if (price <= 0 || gapAmount <= 0) {
            return 0;
        }
        int floored = (int) Math.floor(gapAmount / price);
        if (floored <= 0 && weight > 0) {
            return 1;
        }
        return floored;
    }

    public static void calculateUserWeights(List<EquityHoldings> userHoldings) {
        if (userHoldings == null || userHoldings.isEmpty())
            return;

        // Calculate total value using Current Value (preferred) or Investment Cost
        // (fallback)
        double totalValue = userHoldings.stream()
                .mapToDouble(h -> {
                    if (h.getCurrentValue() != null)
                        return h.getCurrentValue();
                    if (h.getInvestmentCost() != null)
                        return h.getInvestmentCost();
                    return 0.0;
                })
                .sum();

        if (totalValue > 0) {
            userHoldings.forEach(h -> {
                double value = 0.0;
                if (h.getCurrentValue() != null) {
                    value = h.getCurrentValue();
                } else if (h.getInvestmentCost() != null) {
                    value = h.getInvestmentCost();
                }

                h.setWeightInPortfolio((value / totalValue) * 100.0);
            });
        }
    }
}
