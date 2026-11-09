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

        // Prefer current value / investment cost only when strictly positive.
        // Mapper often sets investmentCost=0.0 when avgBuyingPrice is missing (e.g. Dhan).
        double totalValue = userHoldings.stream()
                .mapToDouble(BasketUtils::positiveMoneyValue)
                .sum();

        if (totalValue > 0) {
            userHoldings.forEach(h -> {
                double value = positiveMoneyValue(h);
                h.setWeightInPortfolio((value / totalValue) * 100.0);
            });
            return;
        }

        // Zero-cost books (Dhan): quantity-proportional weights so overlap match can score HELD.
        double totalQty = userHoldings.stream()
                .mapToDouble(h -> h.getQuantity() != null && h.getQuantity() > 0 ? h.getQuantity() : 0.0)
                .sum();
        if (totalQty <= 0) {
            return;
        }
        userHoldings.forEach(h -> {
            double qty = h.getQuantity() != null && h.getQuantity() > 0 ? h.getQuantity() : 0.0;
            h.setWeightInPortfolio((qty / totalQty) * 100.0);
        });
    }

    /** Positive currentValue, else positive investmentCost, else 0. */
    static double positiveMoneyValue(EquityHoldings h) {
        if (h == null) {
            return 0.0;
        }
        if (h.getCurrentValue() != null && h.getCurrentValue() > 0) {
            return h.getCurrentValue();
        }
        if (h.getInvestmentCost() != null && h.getInvestmentCost() > 0) {
            return h.getInvestmentCost();
        }
        return 0.0;
    }
}
