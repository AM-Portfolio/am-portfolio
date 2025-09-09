package com.portfolio.api.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.License;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI portfolioOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("Portfolio Management API")
                        .description("API documentation for the Asset Management Portfolio application")
                        .version("v1.0")
                        .contact(new Contact()
                                .name("Portfolio Team")
                                .email("portfolio-team@example.com"))
                        .license(new License()
                                .name("Private")
                                .url("https://example.com")));
    }
}
