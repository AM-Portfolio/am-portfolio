package com.portfolio.app.integration;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.KafkaContainer;
import org.testcontainers.containers.MongoDBContainer;
import org.testcontainers.utility.DockerImageName;
import com.portfolio.kafka.service.PortfolioCalculationService;
import org.springframework.boot.test.mock.mockito.MockBean;

/**
 * Base class for all integration tests requiring real infrastructure.
 * Uses Testcontainers to spin up MongoDB, Redis, and Kafka.
 *
 * Uses MOCK_MVC webEnvironment so that @AutoConfigureMockMvc works properly
 * and so all sub-classes share the same Spring application context.
 * Infrastructure properties are injected via @DynamicPropertySource.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
public abstract class BaseIntegrationTest {

    @MockBean
    protected PortfolioCalculationService portfolioCalculationService;

    static final MongoDBContainer mongoDBContainer;
    static final KafkaContainer kafkaContainer;
    static final GenericContainer<?> redisContainer;

    static {
        mongoDBContainer = new MongoDBContainer(DockerImageName.parse("mongo:6.0"));
        kafkaContainer = new KafkaContainer(DockerImageName.parse("confluentinc/cp-kafka:7.4.0"))
                .withEnv("KAFKA_OFFSETS_TOPIC_REPLICATION_FACTOR", "1")
                .withEnv("KAFKA_GROUP_INITIAL_REBALANCE_DELAY_MS", "0")
                .withEnv("KAFKA_TRANSACTION_STATE_LOG_REPLICATION_FACTOR", "1")
                .withEnv("KAFKA_TRANSACTION_STATE_LOG_MIN_ISR", "1")
                // Enable auto-topic-creation so consumer subscription doesn't silently fail
                .withEnv("KAFKA_AUTO_CREATE_TOPICS_ENABLE", "true")
                .withStartupTimeout(java.time.Duration.ofSeconds(120));
        redisContainer = new GenericContainer<>(DockerImageName.parse("redis:7.2-alpine"))
                .withExposedPorts(6379);

        // Start containers manually to ensure they are singletons across all test classes
        mongoDBContainer.start();
        kafkaContainer.start();
        redisContainer.start();
    }

    @DynamicPropertySource
    static void setProperties(DynamicPropertyRegistry registry) {
        // MongoDB - mapped to the env-var the application.yml reads
        registry.add("MONGODB_URL", mongoDBContainer::getReplicaSetUrl);

        // Kafka
        registry.add("KAFKA_BOOTSTRAP_SERVERS", kafkaContainer::getBootstrapServers);
        registry.add("KAFKA_SECURITY_PROTOCOL", () -> "PLAINTEXT");
        registry.add("KAFKA_SASL_MECHANISM", () -> "PLAIN");
        registry.add("spring.kafka.consumer.auto-offset-reset", () -> "earliest");
        // Disable SASL/SSL for embedded test broker
        registry.add("spring.kafka.properties.security-protocol", () -> "PLAINTEXT");

        // Redis
        registry.add("REDIS_HOSTNAME", redisContainer::getHost);
        registry.add("REDIS_PORT", () -> redisContainer.getMappedPort(6379).toString());
        registry.add("REDIS_PASSWORD", () -> "");

        // Shorten MongoDB client timeouts so integration tests fail fast instead of waiting 30s
        registry.add("spring.data.mongodb.connect-timeout", () -> "5000");
        registry.add("spring.data.mongodb.socket-timeout", () -> "5000");
        registry.add("spring.data.mongodb.server-selection-timeout", () -> "5000");
    }

    @org.springframework.boot.test.context.TestConfiguration
    static class KafkaTestConfig {
        @org.springframework.context.annotation.Bean
        public org.apache.kafka.clients.admin.NewTopic portfolioTopic() {
            return new org.apache.kafka.clients.admin.NewTopic("am-portfolio", 1, (short) 1);
        }

        @org.springframework.context.annotation.Bean
        public org.apache.kafka.clients.admin.NewTopic stockTopic() {
            return new org.apache.kafka.clients.admin.NewTopic("am-stock-price-update", 1, (short) 1);
        }

        @org.springframework.context.annotation.Bean
        public org.apache.kafka.clients.admin.NewTopic indexTopic() {
            return new org.apache.kafka.clients.admin.NewTopic("nse-stock-indices-update", 1, (short) 1);
        }

        // FIX: Pre-create the trigger-calculation topic so the consumer
        // subscribes correctly before the test sends its message
        @org.springframework.context.annotation.Bean
        public org.apache.kafka.clients.admin.NewTopic triggerCalculationTopic() {
            return new org.apache.kafka.clients.admin.NewTopic("am-trigger-calculation", 1, (short) 1);
        }

        // Pre-create holding update topic used by the holding consumer
        @org.springframework.context.annotation.Bean
        public org.apache.kafka.clients.admin.NewTopic holdingUpdateTopic() {
            return new org.apache.kafka.clients.admin.NewTopic("am-holding-update", 1, (short) 1);
        }
    }
}
