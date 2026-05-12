package com.portfolio.app.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.mongodb.repository.config.EnableMongoRepositories;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;


import org.springframework.context.annotation.Profile;

@Configuration
@Profile("!web-test")
@EnableAsync
@EnableMongoRepositories(basePackages = {
    "com.am.common.amcommondata.repository",
    "com.portfolio" // In case there are local mongo repos in sub-modules
})
public class InfrastructureConfig {
    // Infrastructure beans (Mongo, etc.) are configured here
}
