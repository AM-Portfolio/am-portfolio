// package com.portfolio.config;

// import org.springframework.beans.factory.annotation.Value;
// import org.springframework.context.annotation.Bean;
// import org.springframework.context.annotation.Configuration;
// import org.springframework.data.mongodb.config.AbstractMongoClientConfiguration;
// import org.springframework.data.mongodb.core.MongoTemplate;
// import org.springframework.data.mongodb.repository.config.EnableMongoRepositories;
// import org.springframework.lang.NonNull;

// import com.mongodb.client.MongoClient;
// import com.mongodb.client.MongoClients;

// import lombok.RequiredArgsConstructor;

// @Configuration
// @EnableMongoRepositories(basePackages = "com.am.common.amcommondata.repository.*")
// @RequiredArgsConstructor
// public class MongoConfig extends AbstractMongoClientConfiguration {

//     @Value("${spring.data.mongodb.uri}")
//     private String mongodbUri;

//     @Value("${spring.data.mongodb.database}")
//     private String mongodbDatabase;

//     @Override
//     @NonNull
//     protected String getDatabaseName() {
//         return mongodbDatabase;
//     }

//     @Override
//     @Bean
//     @NonNull
//     public MongoClient mongoClient() {
//         return MongoClients.create(mongodbUri);
//     }

//     @Bean
//     public MongoTemplate mongoTemplate() {
//         return new MongoTemplate(mongoClient(), getDatabaseName());
//     }
// }