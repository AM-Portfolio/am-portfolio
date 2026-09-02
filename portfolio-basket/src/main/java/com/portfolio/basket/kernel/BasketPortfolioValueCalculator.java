package com.portfolio.basket.kernel;

import com.portfolio.model.portfolio.EquityHoldings;
import lombok.Builder;
import lombok.Value;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class BasketPortfolioValueCalculator {

    @Value
    @Builder
    public static class PortfolioValues {
        double totalPortfolioValue;
        double remainingPortfolioValue;
    }

    public PortfolioValues calculate(List<EquityHoldings> userHoldings) {
        List<EquityHoldings> holdings = userHoldings != null ? userHoldings : List.of();
        double totalValue = holdings.stream()
                .mapToDouble(h -> {
                    if (h.getCurrentValue() != null) {
                        return h.getCurrentValue();
                    }
                    if (h.getInvestmentCost() != null) {
                        return h.getInvestmentCost();
                    }
                    return 0.0;
                })
                .sum();

        double remainingValue = holdings.stream()
                .mapToDouble(h -> {
                    double avail = h.getAvailableQuantity() != null ? h.getAvailableQuantity()
                            : (h.getQuantity() != null ? h.getQuantity() : 0.0);
                    double price = (h.getCurrentPrice() != null && h.getCurrentPrice() > 0) ? h.getCurrentPrice()
                            : (h.getAverageBuyingPrice() != null ? h.getAverageBuyingPrice() : 0.0);
                    return avail * price;
                })
                .sum();

        return PortfolioValues.builder()
                .totalPortfolioValue(totalValue)
                .remainingPortfolioValue(remainingValue)
                .build();
    }
}
