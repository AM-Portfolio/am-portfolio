package com.portfolio.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.mongodb.config.AbstractMongoClientConfiguration;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.repository.config.EnableMongoRepositories;
import org.springframework.lang.NonNull;

import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import com.portfolio.config.properties.PersistenceProperties;

import lombok.RequiredArgsConstructor;

@Configuration
@EnableMongoRepositories(basePackages = "com.am.common.amcommondata.repository")
@RequiredArgsConstructor
public class MongoConfig extends AbstractMongoClientConfiguration {

    private final PersistenceProperties persistenceProperties;

    @Override
    @NonNull
    protected String getDatabaseName() {
        return persistenceProperties.getMongodb().getDatabase();
    }

    @Override
    @Bean
    @NonNull
    public MongoClient mongoClient() {
        return MongoClients.create(persistenceProperties.getMongodb().getUri());
    }

    @Bean
    public MongoTemplate mongoTemplate() {
        return new MongoTemplate(mongoClient(), getDatabaseName());
    }
}