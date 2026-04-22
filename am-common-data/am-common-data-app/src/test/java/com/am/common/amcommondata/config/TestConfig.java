package com.am.common.amcommondata.config;

import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;

@Configuration
@EnableAutoConfiguration
@ComponentScan(basePackages = {
    "com.am.common.amcommondata",
    "com.am.common.amcommondata.model",
    "com.am.common.amcommondata.service",
    "com.portfolio"
})
@EntityScan(basePackages = {
    "com.am.common.amcommondata.domain",
    "com.am.common.amcommondata.domain.asset"
})
@EnableJpaRepositories(basePackages = {
    "com.am.common.amcommondata.repository",
    "com.am.common.amcommondata.repository.asset"
})
@Import(MongoTestConfig.class)
public class TestConfig {
    // @Container
    // static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:15.2")
    //         .withDatabaseName("testdb")
    //         .withUsername("test")
    //         .withPassword("test");

    // static {
    //     postgres.start();
    // }

    // @DynamicPropertySource
    // static void configureProperties(DynamicPropertyRegistry registry) {
    //     registry.add("spring.datasource.url", postgres::getJdbcUrl);
    //     registry.add("spring.datasource.username", postgres::getUsername);
    //     registry.add("spring.datasource.password", postgres::getPassword);
    // }
}
