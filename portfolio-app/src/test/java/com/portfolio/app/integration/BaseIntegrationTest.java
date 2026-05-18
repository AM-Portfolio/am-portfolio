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

import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.time.Duration;

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

    private static String localMongoUrl = null;

    private static boolean isPortOpen(String host, int port) {
        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress(host, port), 1000);
            return true;
        } catch (IOException e) {
            return false;
        }
    }

    private static boolean isMongoConnectionValid(String connectionString) {
        try (MongoClient mongoClient = MongoClients.create(connectionString)) {
            // listDatabaseNames().first() forces a SCRAM auth verification synchronously
            mongoClient.listDatabaseNames().first();
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    static {
        boolean localMongo = isPortOpen("localhost", 27017);
        boolean localKafka = isPortOpen("localhost", 9092);
        boolean localRedis = isPortOpen("localhost", 6379);

        if (localMongo) {
            System.out.println(">>> [INTEGRATION TEST] Detected port 27017 open. Probing MongoDB credentials...");
            String urlAdminPassword = "mongodb://admin:admin_password@localhost:27017/portfolio?authSource=admin";
            String urlPassword123 = "mongodb://admin:password123@localhost:27017/portfolio?authSource=admin";

            if (isMongoConnectionValid(urlAdminPassword)) {
                System.out.println(">>> [INTEGRATION TEST] Successfully authenticated MongoDB using 'admin_password'.");
                localMongoUrl = urlAdminPassword;
                mongoDBContainer = null;
            } else if (isMongoConnectionValid(urlPassword123)) {
                System.out.println(">>> [INTEGRATION TEST] Successfully authenticated MongoDB using 'password123'.");
                localMongoUrl = urlPassword123;
                mongoDBContainer = null;
            } else {
                System.out.println(">>> [INTEGRATION TEST] MongoDB credentials probing failed on port 27017. Starting Testcontainers MongoDB...");
                mongoDBContainer = new MongoDBContainer(DockerImageName.parse("mongo:6.0"));
                mongoDBContainer.start();
            }
        } else {
            System.out.println(">>> [INTEGRATION TEST] Local MongoDB port 27017 closed. Starting Testcontainers MongoDB...");
            mongoDBContainer = new MongoDBContainer(DockerImageName.parse("mongo:6.0"));
            mongoDBContainer.start();
        }

        if (localKafka) {
            System.out.println(">>> [INTEGRATION TEST] Detected local running Kafka. Bypassing Testcontainers.");
            kafkaContainer = null;
        } else {
            System.out.println(">>> [INTEGRATION TEST] Local Kafka not detected. Starting Testcontainers Kafka...");
            kafkaContainer = new KafkaContainer(DockerImageName.parse("confluentinc/cp-kafka:7.4.0"))
                    .withEnv("KAFKA_OFFSETS_TOPIC_REPLICATION_FACTOR", "1")
                    .withEnv("KAFKA_GROUP_INITIAL_REBALANCE_DELAY_MS", "0")
                    .withEnv("KAFKA_TRANSACTION_STATE_LOG_REPLICATION_FACTOR", "1")
                    .withEnv("KAFKA_TRANSACTION_STATE_LOG_MIN_ISR", "1")
                    // Enable auto-topic-creation so consumer subscription doesn't silently fail
                    .withEnv("KAFKA_AUTO_CREATE_TOPICS_ENABLE", "true")
                    .withStartupTimeout(Duration.ofSeconds(120));
            kafkaContainer.start();
        }

        if (localRedis) {
            System.out.println(">>> [INTEGRATION TEST] Detected local running Redis. Bypassing Testcontainers.");
            redisContainer = null;
        } else {
            System.out.println(">>> [INTEGRATION TEST] Local Redis not detected. Starting Testcontainers Redis...");
            redisContainer = new GenericContainer<>(DockerImageName.parse("redis:7.2-alpine"))
                    .withExposedPorts(6379);
            redisContainer.start();
        }
    }

    @DynamicPropertySource
    static void setProperties(DynamicPropertyRegistry registry) {
        // MongoDB - mapped to the env-var the application.yml reads
        if (mongoDBContainer != null) {
            registry.add("MONGODB_URL", mongoDBContainer::getReplicaSetUrl);
        } else {
            registry.add("MONGODB_URL", () -> localMongoUrl);
        }

        // Kafka
        if (kafkaContainer != null) {
            registry.add("KAFKA_BOOTSTRAP_SERVERS", kafkaContainer::getBootstrapServers);
        } else {
            registry.add("KAFKA_BOOTSTRAP_SERVERS", () -> "localhost:9092");
        }
        registry.add("KAFKA_SECURITY_PROTOCOL", () -> "PLAINTEXT");
        registry.add("KAFKA_SASL_MECHANISM", () -> "PLAIN");
        registry.add("spring.kafka.consumer.auto-offset-reset", () -> "earliest");
        // Disable SASL/SSL for embedded test broker
        registry.add("spring.kafka.properties.security-protocol", () -> "PLAINTEXT");

        // Redis
        if (redisContainer != null) {
            registry.add("REDIS_HOSTNAME", redisContainer::getHost);
            registry.add("REDIS_PORT", () -> redisContainer.getMappedPort(6379).toString());
        } else {
            registry.add("REDIS_HOSTNAME", () -> "localhost");
            registry.add("REDIS_PORT", () -> "6379");
        }
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
