package com.portfolio.basket.controller;

import com.portfolio.basket.model.BasketOpportunity;
import com.portfolio.basket.service.BasketEngineService;
import com.portfolio.model.portfolio.EquityHoldings;
import lombok.RequiredArgsConstructor;
import lombok.Data;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/basket")
@RequiredArgsConstructor
public class BasketController {

    private final BasketEngineService basketService;

    @PostMapping("/opportunities")
    public List<BasketOpportunity> getOpportunities(@RequestBody List<EquityHoldings> userHoldings) {
        return basketService.findOpportunities(userHoldings);
    }

    @PostMapping("/preview")
    public BasketOpportunity getPreview(@RequestBody PreviewRequest request) {
        // Find specific opportunity logic (reusing findOpportunities for now as it
        // calculates everything)
        // In prod, this would be optimized to checking just one ETF.
        return basketService.findOpportunities(request.getUserHoldings()).stream()
                .filter(o -> o.getEtfIsin().equals(request.getEtfIsin()))
                .findFirst()
                .orElse(null);
    }

    @Data
    public static class PreviewRequest {
        private String etfIsin;
        private List<EquityHoldings> userHoldings;
    }
}
