package com.portfolio.app.util;

import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

/**
 * Utility class to assist with security mocking in Spring MVC Integration Tests.
 * It provides a standardized way to generate dummy JWTs that conform to the format
 * expected by our TokenExtractor logic.
 */
public final class TestSecurityHelper {

    private TestSecurityHelper() {
        // Utility class
    }

    /**
     * Generates a dummy JWT token that contains the provided userId as the 'sub' claim.
     * The token is not cryptographically signed, but it passes the basic split and base64 decode
     * requirements of TokenExtractor.extractAllClaimsUnsafe().
     *
     * @param userId The user ID to embed in the mock token
     * @return A Base64 encoded dummy JWT string
     */
    public static String generateMockJwt(String userId) {
        String header = "{\"alg\":\"none\"}";
        String payload = "{\"sub\":\"" + userId + "\"}";

        String encodedHeader = Base64.getUrlEncoder().withoutPadding().encodeToString(header.getBytes(StandardCharsets.UTF_8));
        String encodedPayload = Base64.getUrlEncoder().withoutPadding().encodeToString(payload.getBytes(StandardCharsets.UTF_8));

        return encodedHeader + "." + encodedPayload + ".dummy_signature";
    }

    /**
     * A Spring MockMvc RequestPostProcessor that automatically injects a mock Authorization header.
     * This is the preferred way to mock an authenticated user in controller integration tests.
     *
     * Example Usage:
     * mockMvc.perform(get("/api/data").with(mockJwtUser("user-123")))
     *
     * @param userId The user ID to simulate
     * @return RequestPostProcessor to append to a mockMvc.perform call
     */
    public static RequestPostProcessor mockJwtUser(String userId) {
        return request -> {
            request.addHeader("Authorization", "Bearer " + generateMockJwt(userId));
            return request;
        };
    }
}
