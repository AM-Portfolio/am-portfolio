package com.portfolio.app.integration;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Integration test for security configuration.
 * Verifies that the security filter chain is correctly configured for stateless operation
 * and that endpoints are accessible according to the current "Relaxed Dev" policy.
 */
@SpringBootTest
@AutoConfigureMockMvc
class SecurityIntegrationTest extends BaseIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void testPublicEndpointAccessibility() throws Exception {
        // Verifying that our current configuration permits all requests
        // We use a dummy userId to trigger a 404 from the controller logic, 
        // which proves it passed the security filter.
        mockMvc.perform(get("/v1/portfolios/list").param("userId", "non-existent-user"))
                .andExpect(status().isNotFound()); 
    }

    @Test
    void testCsrfIsDisabled() throws Exception {
        // Since CSRF is disabled in SecurityConfig, a POST request without a token should NOT return 403.
        // It might return 405 if the path matches a controller but the method is wrong.
        mockMvc.perform(post("/v1/portfolios/dummy"))
                .andExpect(status().isMethodNotAllowed()); // 405 means it reached the dispatcher, not blocked by CSRF
    }

    @Test
    void testSessionIsStateless() throws Exception {
        // Verifying that the response does not contain session-related headers like 'Set-Cookie'
        mockMvc.perform(get("/v1/portfolios/list").param("userId", "any"))
                .andExpect(result -> {
                    String cookie = result.getResponse().getHeader("Set-Cookie");
                    if (cookie != null) {
                        assert !cookie.contains("JSESSIONID");
                    }
                });
    }
}
