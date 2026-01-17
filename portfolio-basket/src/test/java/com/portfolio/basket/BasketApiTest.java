package com.portfolio.basket;

import com.portfolio.basket.controller.BasketController;
import com.portfolio.basket.model.BasketOpportunity;
import com.portfolio.basket.service.BasketEngineService;
import com.portfolio.model.portfolio.EquityHoldings;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

import java.io.File;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(classes = BasketApiTest.TestConfig.class)
public class BasketApiTest {

    @Configuration
    @Import({ BasketEngineService.class, BasketController.class })
    static class TestConfig {
    }

    @Autowired
    private BasketController basketController;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    public void testBasketLogic_Phase2() throws Exception {
        System.out.println("🧪 Executing Basket API Logic Test...");

        // 1. Load Mock User Portfolio A (Nifty 50 Heavy)
        // Path: src/test/resources/mocks/user_portfolio_variant_A.json
        // NOTE: In current dir, path is relative to project root or classpath?
        // Let's use classpath loading if possible, but file io for simplicity relative
        // to execution

        File mockPortfolioFile = new File("src/test/resources/mocks/user_portfolio_variant_A.json");
        assertTrue(mockPortfolioFile.exists(), "Mock Portfolio File A must exist");

        Map<String, Object> portfolioData = objectMapper.readValue(mockPortfolioFile,
                new TypeReference<Map<String, Object>>() {
                });
        List<Map<String, Object>> holdingsMaps = (List<Map<String, Object>>) portfolioData.get("holdings");

        List<EquityHoldings> userHoldings = objectMapper.convertValue(holdingsMaps,
                new TypeReference<List<EquityHoldings>>() {
                });

        // 2. Call API (Controller method directly)
        List<BasketOpportunity> opportunities = basketController.getOpportunities(userHoldings);

        // 3. Verify
        assertNotNull(opportunities);
        System.out.println("Found " + opportunities.size() + " opportunities");

        // Expecting Nifty 50 to match (INF20220202)
        BasketOpportunity nifty50 = opportunities.stream()
                .filter(o -> o.getEtfIsin().equals("INF20220202"))
                .findFirst()
                .orElse(null);

        assertNotNull(nifty50, "Should find Nifty 50 Opportunity");
        System.out.println("Nifty 50 Score: " + nifty50.getMatchScore());

        // In mock data: Nifty 50 has 10 items.
        // User A has: RELIANCE, HDFCBANK, INFY, TCS, ICICIBANK, SBIN, M&M, AXISBANK,
        // LTI.
        // Direct Matches:
        // RELIANCE (Yes), HDFCBANK (Yes), INFY (Yes), TCS (Yes), ICICIBANK (Yes), SBIN
        // (Yes), LTI (Yes), M&M (Yes), ADANIPORTS (No), AXISBANK (Yes).
        // Total 9/10 = 90%.
        // Wait, User A has 8 holdings listed in the json file created in step 116.
        // 1. RELIANCE, 2. HDFCBANK, 3. INFY, 4. TCS, 5. ICICIBANK, 6. SBIN, 7. M&M, 8.
        // AXISBANK.
        // Matches: 8/10 = 80%.

        assertTrue(nifty50.getMatchScore() >= 80.0, "Score should be >= 80%");

        System.out.println("✅ Phase 2 Logic Verified: " + nifty50.getMatchScore() + "%");
    }
}
