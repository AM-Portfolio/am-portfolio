// package com.portfolio.marketdata.client;

// import static org.junit.jupiter.api.Assertions.assertEquals;
// import static org.junit.jupiter.api.Assertions.assertNotNull;
// import static org.mockito.ArgumentMatchers.anyString;
// import static org.mockito.Mockito.when;

// import java.time.LocalDateTime;
// import java.util.List;

// import org.junit.jupiter.api.BeforeEach;
// import org.junit.jupiter.api.Test;
// import org.junit.jupiter.api.extension.ExtendWith;
// import org.mockito.Mock;
// import org.mockito.junit.jupiter.MockitoExtension;
// import org.springframework.web.reactive.function.client.WebClient;
// import org.springframework.web.reactive.function.client.WebClient.RequestHeadersSpec;
// import org.springframework.web.reactive.function.client.WebClient.RequestHeadersUriSpec;
// import org.springframework.web.reactive.function.client.WebClient.ResponseSpec;

// import com.portfolio.marketdata.config.NseIndicesApiConfig;
// import com.portfolio.marketdata.model.indices.AuditInfo;
// import com.portfolio.marketdata.model.indices.IndexConstituent;
// import com.portfolio.marketdata.model.indices.IndexData;
// import com.portfolio.marketdata.model.indices.IndexMetadata;

// import reactor.core.publisher.Mono;
// import reactor.test.StepVerifier;

// @ExtendWith(MockitoExtension.class)
// public class NseIndicesApiClientTest {

//     @Mock
//     private WebClient webClientMock;
    
//     @Mock
//     private RequestHeadersUriSpec<?> requestHeadersUriSpecMock;
    
//     @Mock
//     private RequestHeadersSpec<?> requestHeadersSpecMock;
    
//     @Mock
//     private ResponseSpec responseSpecMock;
    
//     private NseIndicesApiClient nseIndicesApiClient;
//     private NseIndicesApiConfig config;
    
//     @BeforeEach
//     void setUp() {
//         config = new NseIndicesApiConfig();
//         config.setBaseUrl("http://localhost:8084");
//         config.setIndicesPath("/api/v1/nse-indices");
//         config.setConnectionTimeout(5000);
//         config.setReadTimeout(5000);
//         config.setMaxRetryAttempts(3);
        
//         // Create a test instance with our mocked WebClient
//         nseIndicesApiClient = new NseIndicesApiClient(config) {
//             @Override
//             protected WebClient getWebClient() {
//                 return webClientMock;
//             }
//         };
//     }
    
//     @Test
//     void testGetIndexData() {
//         // Prepare test data
//         String indexSymbol = "NIFTY50";
        
//         IndexConstituent constituent1 = new IndexConstituent();
//         constituent1.setSymbol("INFY");
//         constituent1.setName("Infosys Ltd.");
//         constituent1.setIndustry("IT");
        
//         IndexConstituent constituent2 = new IndexConstituent();
//         constituent2.setSymbol("TCS");
//         constituent2.setName("Tata Consultancy Services Ltd.");
//         constituent2.setIndustry("IT");
        
//         IndexMetadata metadata = new IndexMetadata();
//         metadata.setIndexName("NIFTY 50");
//         metadata.setOpen(18500.0);
//         metadata.setHigh(18600.0);
//         metadata.setLow(18400.0);
//         metadata.setPercChange(0.5);
        
//         AuditInfo auditInfo = new AuditInfo();
//         auditInfo.setCreatedAt(LocalDateTime.now());
//         auditInfo.setUpdatedAt(LocalDateTime.now());
        
//         IndexData indexData = new IndexData();
//         indexData.setIndexSymbol(indexSymbol);
//         indexData.setData(List.of(constituent1, constituent2));
//         indexData.setMetadata(metadata);
//         indexData.setAudit(auditInfo);
        
//         // Mock WebClient behavior
//         when(webClientMock.get()).thenReturn(requestHeadersUriSpecMock);
//         when(requestHeadersUriSpecMock.uri(anyString())).thenReturn(requestHeadersSpecMock);
//         when(requestHeadersSpecMock.retrieve()).thenReturn(responseSpecMock);
//         when(responseSpecMock.bodyToMono(IndexData.class)).thenReturn(Mono.just(indexData));
        
//         // Test the method
//         Mono<IndexData> result = nseIndicesApiClient.getIndexData(indexSymbol);
        
//         // Verify the result
//         StepVerifier.create(result)
//             .assertNext(data -> {
//                 assertNotNull(data);
//                 assertEquals(indexSymbol, data.getIndexSymbol());
//                 assertEquals(2, data.getData().size());
//                 assertEquals("INFY", data.getData().get(0).getSymbol());
//                 assertEquals("TCS", data.getData().get(1).getSymbol());
//                 assertEquals("IT", data.getData().get(0).getIndustry());
//                 assertEquals("NIFTY 50", data.getMetadata().getIndexName());
//             })
//             .verifyComplete();
//     }
    
//     @Test
//     void testGetIndexDataSync() {
//         // Prepare test data
//         String indexSymbol = "NIFTY50";
        
//         IndexConstituent constituent1 = new IndexConstituent();
//         constituent1.setSymbol("INFY");
//         constituent1.setName("Infosys Ltd.");
//         constituent1.setIndustry("IT");
        
//         IndexMetadata metadata = new IndexMetadata();
//         metadata.setIndexName("NIFTY 50");
        
//         IndexData indexData = new IndexData();
//         indexData.setIndexSymbol(indexSymbol);
//         indexData.setData(List.of(constituent1));
//         indexData.setMetadata(metadata);
        
//         // Mock WebClient behavior
//         when(webClientMock.get()).thenReturn(requestHeadersUriSpecMock);
//         when(requestHeadersUriSpecMock.uri(anyString())).thenReturn(requestHeadersSpecMock);
//         when(requestHeadersSpecMock.retrieve()).thenReturn(responseSpecMock);
//         when(responseSpecMock.bodyToMono(IndexData.class)).thenReturn(Mono.just(indexData));
        
//         // Test the method
//         IndexData result = nseIndicesApiClient.getIndexDataSync(indexSymbol);
        
//         // Verify the result
//         assertNotNull(result);
//         assertEquals(indexSymbol, result.getIndexSymbol());
//         assertEquals(1, result.getData().size());
//         assertEquals("INFY", result.getData().get(0).getSymbol());
//         assertEquals("IT", result.getData().get(0).getIndustry());
//     }
// }
