package com.portfolio.basket.service;

import com.portfolio.basket.model.BasketOpportunity;
import com.portfolio.model.portfolio.EquityHoldings;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Facade for basket engine operations; delegates to {@link BasketEngineService} during kernel split.
 */
@Service
@RequiredArgsConstructor
public class BasketEngineFacade {

    private final BasketEngineService basketEngineService;

    public List<BasketOpportunity> findOpportunities(List<EquityHoldings> userHoldings, String etfQuery) {
        return basketEngineService.findOpportunities(userHoldings, etfQuery);
    }

    public BasketOpportunity getPreview(String etfIsin, List<EquityHoldings> userHoldings) {
        return basketEngineService.getPreview(etfIsin, userHoldings);
    }

    public BasketOpportunity calculateBasketQuantities(
            Double investmentAmount,
            BasketOpportunity opportunity,
            boolean includeHeld,
            List<String> excludedSymbols) {
        return basketEngineService.calculateBasketQuantities(investmentAmount, opportunity, includeHeld, excludedSymbols);
    }

    public BasketOpportunity applySubstitutesOnExisting(
            BasketOpportunity base,
            List<EquityHoldings> userHoldings,
            List<com.portfolio.basket.model.SubstituteAssignment> assignments) {
        return basketEngineService.applySubstitutesOnExisting(base, userHoldings, assignments);
    }
}
