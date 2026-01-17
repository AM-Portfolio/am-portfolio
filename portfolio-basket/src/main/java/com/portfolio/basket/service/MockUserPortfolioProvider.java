package com.portfolio.basket.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.portfolio.model.portfolio.EquityHoldings;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.Collections;
import java.util.List;
import java.util.Map;

@Service
@Slf4j
public class MockUserPortfolioProvider implements UserPortfolioProvider {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public List<String> getAllActiveUsers() {
        // Return a mock user ID list
        return List.of("USER001");
    }

    @Override
    public List<EquityHoldings> getUserHoldings(String userId) {
        if ("USER001".equals(userId)) {
            try {
                // Reuse the logic from the test to load the mock file from resources
                // Assuming the file is copied to target/classes/mocks during build
                ClassPathResource resource = new ClassPathResource("mocks/user_portfolio_variant_A.json");
                if (resource.exists()) {
                    Map<String, Object> portfolioData = objectMapper.readValue(resource.getInputStream(),
                            new TypeReference<Map<String, Object>>() {
                            });

                    List<Map<String, Object>> holdingsMaps = (List<Map<String, Object>>) portfolioData.get("holdings");

                    return objectMapper.convertValue(holdingsMaps,
                            new TypeReference<List<EquityHoldings>>() {
                            });
                } else {
                    log.warn("Mock user data not found");
                }
            } catch (IOException e) {
                log.error("Failed to load mock user data", e);
            }
        }
        return Collections.emptyList();
    }
}
