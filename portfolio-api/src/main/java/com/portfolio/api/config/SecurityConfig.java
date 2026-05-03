package com.portfolio.api.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.security.web.util.matcher.AntPathRequestMatcher;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;

/**
 * Spring Security Configuration for Portfolio Service
 * 
 * Security Model: Zero Trust
 * - Validates JWT signature using INTERNAL_JWT_SECRET
 * - Trusts API Gateway ONLY if it presents a valid, signed Service Token
 * - Protected endpoints require valid JWT with correct signature
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

        @Value("${app.jwt.secret}")
        private String jwtSecret;

        @Bean
        @Order(Ordered.HIGHEST_PRECEDENCE)
        public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
                http
                                // Disable CSRF (stateless REST API with JWT)
                                .csrf(csrf -> csrf.disable())

                                // Stateless session management (no cookies, JWT-based)
                                .sessionManagement(session -> session
                                                .sessionCreationPolicy(SessionCreationPolicy.STATELESS))

                                // Configure authorization rules
                                .authorizeHttpRequests(auth -> auth
                                                // ✅ PUBLIC ENDPOINTS - No authentication required
                                                .requestMatchers(
                                                                AntPathRequestMatcher.antMatcher("/api/v1/basket/**"),
                                                                AntPathRequestMatcher.antMatcher("/actuator/health/**"),
                                                                AntPathRequestMatcher.antMatcher("/swagger-ui/**"),
                                                                AntPathRequestMatcher.antMatcher("/v3/api-docs/**"),
                                                                AntPathRequestMatcher.antMatcher("/api-docs/**"),
                                                                AntPathRequestMatcher.antMatcher("/error"))
                                                .permitAll()

                                                // ✅ PROTECTED ENDPOINTS - Require valid JWT
                                                .requestMatchers(
                                                                AntPathRequestMatcher.antMatcher("/api/v1/portfolios/**"),
                                                                AntPathRequestMatcher.antMatcher("/api/v1/portfolio-analytics/**"),
                                                                AntPathRequestMatcher.antMatcher("/api/v1/market-data/**"),
                                                                AntPathRequestMatcher.antMatcher("/api/v1/market-index/**"),
                                                                AntPathRequestMatcher.antMatcher("/api/v1/index-analytics/**"))
                                                .authenticated()

                                                // ❌ Deny all other endpoints (fail secure)
                                                .anyRequest().denyAll())

                                // ✅ ZERO TRUST: Enforce JWT Validation
                                .oauth2ResourceServer(oauth2 -> oauth2.jwt(jwt -> jwt.decoder(jwtDecoder())))

                                // Disable HTTP Basic authentication (not needed, using JWT)
                                .httpBasic(basic -> basic.disable())

                                // Disable form login (API Gateway handles authentication)
                                .formLogin(form -> form.disable());

                return http.build();
        }

        @Bean
        public JwtDecoder jwtDecoder() {
                // Use HS256 (Symmetric Key) to match Auth Service
                SecretKey key = new SecretKeySpec(jwtSecret.getBytes(), "HmacSHA256");
                return NimbusJwtDecoder.withSecretKey(key).build();
        }
}
