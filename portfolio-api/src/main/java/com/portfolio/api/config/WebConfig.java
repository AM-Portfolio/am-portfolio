package com.portfolio.api.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import org.springframework.web.filter.CorsFilter;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;


@Configuration
public class WebConfig implements WebMvcConfigurer {

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/**")
                .allowedOriginPatterns(
                    "http://localhost:[*]", // Allow all localhost ports
                    "https://5f24d53f-cb43-496a-a862-fdc3c9abe7a5-00-3tl6t5sjpcmd2.pike.replit.dev",
                    "https://*.replit.dev",
                    "https://*"
                )
                .allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS")
                .allowedHeaders("*")
                .allowCredentials(true)
                .maxAge(3600);
    }

    @Bean
    public CorsFilter corsFilter() {
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        CorsConfiguration config = new CorsConfiguration();
        
        // Allow localhost and specific HTTPS endpoint
        config.setAllowCredentials(true);
        config.addAllowedOriginPattern("http://localhost:[*]"); // Allow all localhost ports
        config.addAllowedOriginPattern("https://5f24d53f-cb43-496a-a862-fdc3c9abe7a5-00-3tl6t5sjpcmd2.pike.replit.dev");
        config.addAllowedOriginPattern("https://*.replit.dev");
        config.addAllowedOriginPattern("https://*");
        config.addAllowedHeader("*");
        config.addAllowedMethod("*");
        config.setMaxAge(3600L);
        
        source.registerCorsConfiguration("/**", config);
        return new CorsFilter(source);
    }
}
