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
 * <p><b>Security Model: Gateway-Enforced Authentication</b>
 *
 * <p>This service is an internal microservice deployed exclusively behind
 * {@code am-gateway}, which validates JWTs and enforces auth at the edge
 * before forwarding requests. Direct external access is blocked by the
 * infrastructure firewall / Traefik routing rules.
 *
 * <p>All endpoints are therefore open at the service level ({@code permitAll()}).
 * The {@code jwtDecoder()} bean is available if per-service auth is needed in future.
 *
 * <p><b>TODO (future):</b> When the platform matures, add internal-service
 * token validation using {@code X-Internal-Token} or mTLS.
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
            .csrf(csrf -> csrf.disable())
            .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(auth -> auth
                // am-portfolio is behind am-gateway which enforces JWT at the edge.
                // All endpoints are open at the service layer — firewall blocks direct access.
                .requestMatchers(AntPathRequestMatcher.antMatcher("/**")).permitAll()
                .anyRequest().authenticated()
            )
            .httpBasic(basic -> basic.disable())
            .formLogin(form -> form.disable());

        // JWT decoder is wired but not activated at service level.
        // am-gateway is the authentication boundary for this service.
        // Uncomment below to enable per-service JWT validation if needed:
        // .oauth2ResourceServer(oauth2 -> oauth2.jwt(jwt -> jwt.decoder(jwtDecoder())));

        return http.build();
    }

    @Bean
    public JwtDecoder jwtDecoder() {
        SecretKey key = new SecretKeySpec(jwtSecret.getBytes(), "HmacSHA256");
        return NimbusJwtDecoder.withSecretKey(key).build();
    }
}
