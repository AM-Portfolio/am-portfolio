// package com.am.common.amcommondata.service;

// import static org.assertj.core.api.Assertions.assertThat;

// import java.time.LocalDateTime;
// import java.util.HashMap;
// import java.util.List;
// import java.util.Map;
// import java.util.Optional;
// import java.util.UUID;
// import java.util.stream.Collectors;

// import org.junit.jupiter.api.BeforeEach;
// import org.junit.jupiter.api.Test;
// import org.springframework.beans.factory.annotation.Autowired;
// import org.springframework.boot.test.context.SpringBootTest;
// import org.springframework.test.context.DynamicPropertyRegistry;
// import org.springframework.test.context.DynamicPropertySource;
// import org.testcontainers.containers.MongoDBContainer;
// import org.testcontainers.junit.jupiter.Container;
// import org.testcontainers.junit.jupiter.Testcontainers;
// import org.testcontainers.utility.DockerImageName;

// import com.am.common.amcommondata.AmCommonDataApplication;
// import com.am.common.amcommondata.model.MarketCapType;
// import com.am.common.amcommondata.model.security.CompanyInfoModel;
// import com.am.common.amcommondata.model.security.SecurityKeyModel;
// import com.am.common.amcommondata.model.security.SecurityMetadataModel;
// import com.am.common.amcommondata.model.security.SecurityModel;

// @SpringBootTest(classes = AmCommonDataApplication.class)
// @Testcontainers
// class SecurityServiceContainerTest {

//     @Container
//     static MongoDBContainer mongoDBContainer = new MongoDBContainer(DockerImageName.parse("mongo:6.0"));

//     @DynamicPropertySource
//     static void setProperties(DynamicPropertyRegistry registry) {
//         registry.add("spring.data.mongodb.uri", mongoDBContainer::getReplicaSetUrl);
//     }

//     @Autowired
//     private SecurityService securityService;

//     @BeforeEach
//     void setup() {
//         securityService.deleteAll();
//     }

//     @Test
//     void shouldSaveAndRetrieveSecurityModel() {
//         // Given
//         SecurityModel security = createTestSecurityModel();

//         // When
//         SecurityModel savedSecurity = securityService.save(security);
//         Optional<SecurityModel> foundSecurity = securityService.findByIsin(savedSecurity.getKey().getIsin());
//         Optional<SecurityModel> foundBySymbol = securityService.findBySymbol(security.getKey().getSymbol());
//         Optional<SecurityModel> foundByIsin = securityService.findByIsin(security.getKey().getIsin());

//         // Then
//         assertThat(foundSecurity).isPresent();
//         SecurityModel retrievedSecurity = foundSecurity.get();

//         // Verify key info
//         assertThat(retrievedSecurity.getKey().getSymbol()).isEqualTo("AAPL");
//         assertThat(retrievedSecurity.getKey().getIsin()).isEqualTo("US0378331005");

//         // Verify metadata
//         assertThat(retrievedSecurity.getMetadata().getSector()).isEqualTo("Technology");
//         assertThat(retrievedSecurity.getMetadata().getIndustry()).isEqualTo("Consumer Electronics");
//         assertThat(retrievedSecurity.getMetadata().getMarketCapValue()).isEqualTo(3000000000000L);

//         // Verify company info
//         assertThat(retrievedSecurity.getCompanyInfo().getLegalName()).isEqualTo("Apple Inc.");
//         assertThat(retrievedSecurity.getCompanyInfo().getWebsite()).isEqualTo("www.apple.com");

//         // Verify audit metadata
//         assertThat(retrievedSecurity.getAudit()).isNotNull();
//         assertThat(retrievedSecurity.getAudit().getVersion()).isEqualTo(1L);
//         assertThat(retrievedSecurity.getAudit().getCreatedAt()).isNotNull();

//         // Verify index queries work
//         assertThat(foundBySymbol).isPresent();
//         assertThat(foundByIsin).isPresent();
//     }

//     @Test
//     void shouldFindSecuritiesByMarketCapAndSector() {
//         // Given
//         SecurityModel apple = createTestSecurityModel(); // 3T market cap

//         SecurityModel microsoft = createTestSecurityModel();
//         microsoft.getKey().setSymbol("MSFT");
//         microsoft.getKey().setIsin("US5949181045");
//         microsoft.getMetadata().setMarketCapValue(Double.valueOf(2800000000000L));
//         microsoft.getCompanyInfo().setLegalName("Microsoft Corporation");

//         SecurityModel amazon = createTestSecurityModel();
//         amazon.getKey().setSymbol("AMZN");
//         amazon.getKey().setIsin("US0231351067");
//         amazon.getMetadata().setMarketCapValue(Double.valueOf(1500000000000L));
//         amazon.getCompanyInfo().setLegalName("Amazon.com Inc.");

//         SecurityModel walmart = createTestSecurityModel();
//         walmart.getKey().setSymbol("WMT");
//         walmart.getKey().setIsin("US9311421039");
//         walmart.getMetadata().setMarketCapValue(Double.valueOf(400000000000L));
//         walmart.getMetadata().setSector("Consumer Defensive");
//         walmart.getCompanyInfo().setLegalName("Walmart Inc.");

//         securityService.saveAll(List.of(apple, microsoft, amazon, walmart));

//         // When - Find large tech companies
//         List<SecurityModel> largeTechCompanies = securityService
//             .findActiveLargeCapsByMinMarketCapAndSector(2000000000000L, "Technology");

//         // Then
//         assertThat(largeTechCompanies).hasSize(2);
//         assertThat(largeTechCompanies).extracting(s -> s.getKey().getSymbol())
//                                      .containsExactly("AAPL", "MSFT");
//         assertThat(largeTechCompanies).extracting(s -> s.getMetadata().getMarketCapValue())
//                                      .allMatch(cap -> cap >= 2000000000000L);
//     }
    
//     @Test
//     void shouldFindSecuritiesBySymbols() {
//         // Given
//         SecurityModel apple = createTestSecurityModel(); // AAPL
//         apple.setId(UUID.randomUUID()); // Ensure unique ID
        
//         SecurityModel microsoft = createTestSecurityModel();
//         microsoft.setId(UUID.randomUUID()); // Ensure unique ID
//         microsoft.getKey().setSymbol("MSFT");
//         microsoft.getKey().setIsin("US5949181045");
//         microsoft.getCompanyInfo().setLegalName("Microsoft Corporation");
        
//         // Save securities individually to avoid batch issues
//         securityService.save(apple);
//         securityService.save(microsoft);
        
//         // When - Find securities by symbols
//         List<SecurityModel> foundSecurities = securityService.findBySymbols(List.of("AAPL", "MSFT", "UNKNOWN"));
        
//         // Then
//         assertThat(foundSecurities).isNotNull();
//         assertThat(foundSecurities.size()).isEqualTo(2);
        
//         // Extract symbols for easier assertion
//         List<String> foundSymbols = foundSecurities.stream()
//             .map(s -> s.getKey().getSymbol())
//             .collect(Collectors.toList());
            
//         assertThat(foundSymbols).contains("AAPL", "MSFT");
        
//         // Verify company names by finding each security in the result list
//         for (SecurityModel security : foundSecurities) {
//             String symbol = security.getKey().getSymbol();
//             if ("AAPL".equals(symbol)) {
//                 assertThat(security.getCompanyInfo().getLegalName()).isEqualTo("Apple Inc.");
//             } else if ("MSFT".equals(symbol)) {
//                 assertThat(security.getCompanyInfo().getLegalName()).isEqualTo("Microsoft Corporation");
//             }
//         }
//     }

//     private SecurityModel createTestSecurityModel() {
//         SecurityKeyModel keyInfo = SecurityKeyModel.builder()
//             .symbol("AAPL")
//             .isin("US0378331005")
//             .build();

//         SecurityMetadataModel metadata = SecurityMetadataModel.builder()
//             .sector("Technology")
//             .industry("Consumer Electronics")
//             .marketCapValue(Double.valueOf(3000000000000L))
//             .marketCapType(MarketCapType.LARGE_CAP)
//             .securityType("EQUITY")
//             .exchange("NASDAQ")
//             .countryOfIncorporation("USA")
//             .listingDate(LocalDateTime.now().minusYears(40))
//             .tradingStatus("ACTIVE")
//             .customAttributes(new HashMap<>())
//             .build();

//         CompanyInfoModel companyInfo = CompanyInfoModel.builder()
//             .legalName("Apple Inc.")
//             .website("www.apple.com")
//             .build();

//         return SecurityModel.builder()
//         .id(UUID.randomUUID())
//             .key(keyInfo)
//             .metadata(metadata)
//             .companyInfo(companyInfo)
//             .build();
//     }
// }
