// package com.portfolio.service;

// import static org.assertj.core.api.Assertions.assertThat;
// import static org.awaitility.Awaitility.await;

// import java.io.IOException;
// import java.time.LocalDateTime;
// import java.time.ZoneOffset;
// import java.util.List;
// import java.util.Optional;
// import java.util.concurrent.TimeUnit;

// import org.junit.jupiter.api.AfterEach;
// import org.junit.jupiter.api.BeforeEach;
// import org.junit.jupiter.api.Test;
// import org.springframework.beans.factory.annotation.Autowired;
// import org.springframework.boot.test.context.SpringBootTest;
// import org.springframework.context.annotation.Import;
// import org.springframework.test.context.DynamicPropertyRegistry;
// import org.springframework.test.context.DynamicPropertySource;
// import org.testcontainers.containers.GenericContainer;
// import org.testcontainers.containers.KafkaContainer;
// import org.testcontainers.junit.jupiter.Container;
// import org.testcontainers.junit.jupiter.Testcontainers;
// import org.testcontainers.utility.DockerImageName;

// import com.am.common.investment.model.equity.EquityPrice;
// import com.portfolio.PortfolioServiceApplication;
// import com.portfolio.config.TestConfig;
// import com.portfolio.model.StockPriceCache;

// @SpringBootTest(classes = PortfolioServiceApplication.class)
// @Import(TestConfig.class)
// @Testcontainers
// public class StockPriceRedisServiceIntegrationTest {

//     private static final String SYMBOL = "AAPL";
//     private static final int REDIS_PORT = 6379;

//     @Container
//     static GenericContainer<?> redis = new GenericContainer<>(DockerImageName.parse("redis:7.0-alpine"))
//             .withExposedPorts(REDIS_PORT);

//     @Container
//     static KafkaContainer kafka = new KafkaContainer(DockerImageName.parse("confluentinc/cp-kafka:7.3.0"));

//     @DynamicPropertySource
//     static void registerProperties(DynamicPropertyRegistry registry) {
//         registry.add("spring.data.redis.host", redis::getHost);
//         registry.add("spring.data.redis.port", redis::getFirstMappedPort);
//         registry.add("spring.kafka.bootstrap-servers", kafka::getBootstrapServers);
//     }

//     @Autowired
//     private StockPriceRedisService stockPriceRedisService;

//     @BeforeEach
//     void setUp() throws IOException, InterruptedException {
//         // Clear Redis before each test
//         redis.execInContainer("redis-cli", "FLUSHALL");
//     }

//     @AfterEach
//     void tearDown() throws IOException, InterruptedException {
//         // Clear Redis after each test
//         redis.execInContainer("redis-cli", "FLUSHALL");
//     }

//     @Test
//     void shouldCacheAndRetrieveLatestPrice() {
//         // Given
//         EquityPrice priceUpdate = createEquityPrice(100.0, LocalDateTime.now(ZoneOffset.UTC));
//         List<EquityPrice> updates = List.of(priceUpdate);

//         // When
//         stockPriceRedisService.cacheEquityPriceUpdateBatch(updates);

//         // Then
//         await().atMost(5, TimeUnit.SECONDS).untilAsserted(() -> {
//             Optional<StockPriceCache> latestPrice = stockPriceRedisService.getLatestPrice(SYMBOL);
//             assertThat(latestPrice).isPresent();
//             assertThat(latestPrice.get().getClosePrice()).isEqualTo(100.0);
//         });
//     }

//     @Test
//     void shouldRetrieveHistoricalPrices() {
//         // Given
//         LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC);
//         List<EquityPrice> updates = List.of(
//             createEquityPrice(100.0, now.minusHours(2)),
//             createEquityPrice(101.0, now.minusHours(1)),
//             createEquityPrice(102.0, now)
//         );

//         // When
//         stockPriceRedisService.cacheEquityPriceUpdateBatch(updates);

//         // Then
//         await().atMost(5, TimeUnit.SECONDS).untilAsserted(() -> {
//             List<StockPriceCache> historicalPrices = stockPriceRedisService.getHistoricalPrices(
//                 SYMBOL, 
//                 now.minusHours(3), 
//                 now.plusHours(1)
//             );
//             assertThat(historicalPrices).hasSize(3);
//             assertThat(historicalPrices)
//                 .extracting(StockPriceCache::getClosePrice)
//                 .containsExactlyInAnyOrder(100.0, 101.0, 102.0);
//         });
//     }

//     @Test
//     void shouldProcessLargeBatchOfPrices() {
//         // Given
//         List<EquityPrice> updates = generateLargeBatchOfPrices(500);

//         // When
//         stockPriceRedisService.cacheEquityPriceUpdateBatch(updates);

//         // Then
//         await().atMost(10, TimeUnit.SECONDS).untilAsserted(() -> {
//             Optional<StockPriceCache> latestPrice = stockPriceRedisService.getLatestPrice(SYMBOL);
//             assertThat(latestPrice).isPresent();
            
//             List<StockPriceCache> historicalPrices = stockPriceRedisService.getHistoricalPrices(
//                 SYMBOL,
//                 LocalDateTime.now(ZoneOffset.UTC).minusDays(1),
//                 LocalDateTime.now(ZoneOffset.UTC).plusDays(1)
//             );
//             assertThat(historicalPrices).hasSize(500);
//         });
//     }

//     @Test
//     void shouldUpdateLatestPriceWhenNewPriceReceived() {
//         // Given
//         EquityPrice initialPrice = createEquityPrice(100.0, LocalDateTime.now(ZoneOffset.UTC));
//         EquityPrice updatedPrice = createEquityPrice(150.0, LocalDateTime.now(ZoneOffset.UTC).plusMinutes(1));

//         // When
//         stockPriceRedisService.cacheEquityPriceUpdateBatch(List.of(initialPrice));
//         stockPriceRedisService.cacheEquityPriceUpdateBatch(List.of(updatedPrice));

//         // Then
//         await().atMost(10, TimeUnit.SECONDS).untilAsserted(() -> {
//             Optional<StockPriceCache> latestPrice = stockPriceRedisService.getLatestPrice(SYMBOL);
//             assertThat(latestPrice).isPresent();
//             assertThat(latestPrice.get().getClosePrice()).isEqualTo(150.0);
//         });
//     }

//     private List<EquityPrice> generateLargeBatchOfPrices(int count) {
//         return java.util.stream.IntStream.range(0, count)
//             .mapToObj(i -> createEquityPrice(
//                 100.0 + i,
//                 LocalDateTime.now(ZoneOffset.UTC).plusMinutes(i)))
//             .toList();
//     }

//     private EquityPrice createEquityPrice(double price, LocalDateTime timestamp) {
//         return EquityPrice.builder()
//             .symbol(SYMBOL)
//             .close(price)
//             .time(timestamp.toInstant(ZoneOffset.UTC))
//             .volume(1000L)
//             .build();
//     }
// }
