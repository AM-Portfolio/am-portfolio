package com.portfolio.basket.util;

import com.portfolio.model.portfolio.EquityHoldings;
import java.util.List;

public class BasketUtils {

    public static double round(double value) {
        return Math.round(value * 100.0) / 100.0;
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
