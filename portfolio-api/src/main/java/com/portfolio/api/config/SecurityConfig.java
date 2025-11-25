package com.portfolio.api.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Spring Security Configuration for Portfolio Service
 * 
 * Per coding instructions (Service Communication Flow):
 * - API Gateway validates user JWT and generates service JWT
 * - API Gateway passes service JWT to this service via Authorization header
 * - API Gateway passes user_id via X-User-ID header
 * - This service TRUSTS the API Gateway (no manual JWT validation needed)
 * - Protected endpoints require Authorization header (service JWT from gateway)
 * - All requests must come through API Gateway (internal service, no direct access)
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
            // Disable CSRF (stateless REST API with JWT)
            .csrf(csrf -> csrf.disable())
            
            // Stateless session management (no cookies, JWT-based)
            .sessionManagement(session -> 
                session.sessionCreationPolicy(SessionCreationPolicy.STATELESS)
            )
            
            // Configure authorization rules
            .authorizeHttpRequests(auth -> auth
                // ✅ PUBLIC ENDPOINTS - No authentication required
                .requestMatchers(
                    "/actuator/health",             // Docker health check
                    "/actuator/health/live",        // Kubernetes liveness probe
                    "/actuator/health/ready",       // Kubernetes readiness probe
                    "/swagger-ui/**",               // Swagger API documentation
                    "/v3/api-docs/**",              // OpenAPI specification
                    "/v3/api-docs.yaml"             // OpenAPI YAML
                ).permitAll()
                
                // ✅ PROTECTED ENDPOINTS - Require service JWT from API Gateway
                // All portfolio endpoints require authentication
                .requestMatchers(
                    "/api/v1/portfolios/**",              // All portfolio operations
                    "/api/v1/portfolio-analytics/**",     // Analytics endpoints
                    "/api/v1/market-data/**",             // Market data endpoints
                    "/api/v1/market-index/**",            // Market index endpoints
                    "/api/v1/index-analytics/**"          // Index analytics endpoints
                ).authenticated()  // Spring Security checks Authorization header exists
                
                // ❌ Deny all other endpoints (fail secure)
                .anyRequest().denyAll()
            )
            
            // Disable HTTP Basic authentication (not needed, using JWT)
            .httpBasic(basic -> basic.disable())
            
            // Disable form login (API Gateway handles authentication)
            .formLogin(form -> form.disable());
        
        return http.build();
    }
}
