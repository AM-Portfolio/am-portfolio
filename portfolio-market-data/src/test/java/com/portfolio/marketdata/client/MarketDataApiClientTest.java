// package com.portfolio.marketdata.client;

// import com.portfolio.marketdata.config.MarketDataApiConfig;
// import com.portfolio.marketdata.model.FilterType;
// import com.portfolio.marketdata.model.HistoricalDataRequest;
// import com.portfolio.marketdata.model.InstrumentType;
// import com.portfolio.model.market.TimeFrame;

// import org.junit.jupiter.api.BeforeEach;
// import org.junit.jupiter.api.Test;
// import org.mockito.ArgumentCaptor;
// import org.mockito.Captor;
// import org.mockito.InjectMocks;
// import org.mockito.Mock;
// import org.mockito.MockitoAnnotations;
// import org.springframework.web.reactive.function.client.WebClient;
// import reactor.core.publisher.Mono;

// import java.lang.reflect.Field;
// import java.time.LocalDate;
// import java.util.Arrays;
// import java.util.List;

// import static org.junit.jupiter.api.Assertions.assertEquals;
// import static org.junit.jupiter.api.Assertions.assertTrue;
// import static org.mockito.ArgumentMatchers.any;
// import static org.mockito.ArgumentMatchers.anyString;
// import static org.mockito.ArgumentMatchers.eq;
// import static org.mockito.Mockito.mock;
// import static org.mockito.Mockito.verify;
// import static org.mockito.Mockito.when;

// public class MarketDataApiClientTest {

//     @Mock
//     private WebClient webClient;

//     @Mock
//     private WebClient.RequestHeadersUriSpec requestHeadersUriSpec;

//     @Mock
//     private WebClient.RequestHeadersSpec requestHeadersSpec;

//     @Mock
//     private WebClient.ResponseSpec responseSpec;

//     @Mock
//     private MarketDataApiConfig config;

//     @InjectMocks
//     private MarketDataApiClient marketDataApiClient;

//     @Captor
//     private ArgumentCaptor<String> uriCaptor;

//     @BeforeEach
//     public void setup() throws Exception {
//         MockitoAnnotations.openMocks(this);
        
//         // Set up the WebClient mock chain
//         when(webClient.get()).thenReturn(requestHeadersUriSpec);
//         when(requestHeadersUriSpec.uri(anyString())).thenReturn(requestHeadersSpec);
//         when(requestHeadersSpec.retrieve()).thenReturn(responseSpec);
//         when(responseSpec.bodyToMono(any(Class.class))).thenReturn(Mono.empty());
        
//         // Set up config
//         when(config.getHistoricalDataPath()).thenReturn("/api/v1/market-data/historical-data");
        
//         // Inject WebClient into the client
//         Field webClientField = MarketDataApiClient.class.getDeclaredField("webClient");
//         webClientField.setAccessible(true);
//         webClientField.set(marketDataApiClient, webClient);
//     }

//     @Test
//     public void testGetHistoricalDataWithRequest() {
//         // Create a request similar to the curl command
//         HistoricalDataRequest request = HistoricalDataRequest.builder()
//                 .symbols(Arrays.asList("NIFTY 50", "TCS"))
//                 .fromDate(LocalDate.of(2023, 1, 1))
//                 .toDate(LocalDate.of(2023, 1, 31))
//                 .timeFrame(TimeFrame.DAY)
//                 .instrumentType(InstrumentType.EQ)
//                 .filterType(FilterType.CUSTOM)
//                 .filterFrequency(4)
//                 .refresh(false)
//                 .continuous(false)
//                 .build();

//         // Call the method
//         marketDataApiClient.getHistoricalData(request);

//         // Verify the URI construction
//         verify(requestHeadersUriSpec).uri(uriCaptor.capture());
//         String capturedUri = uriCaptor.getValue();
        
//         // Verify all parameters are included correctly
//         assertTrue(capturedUri.contains("symbols=NIFTY%2050,TCS"));
//         assertTrue(capturedUri.contains("from=2023-01-01"));
//         assertTrue(capturedUri.contains("to=2023-01-31"));
//         assertTrue(capturedUri.contains("interval=day"));
//         assertTrue(capturedUri.contains("instrumentType=EQ"));
//         assertTrue(capturedUri.contains("filterType=CUSTOM"));
//         assertTrue(capturedUri.contains("filterFrequency=4"));
//         assertTrue(capturedUri.contains("continuous=false"));
//         assertTrue(capturedUri.contains("refresh=false"));
//     }

//     @Test
//     public void testFilterFrequencyOnlyIncludedWhenFilterTypeIsCustom() {
//         // Test with CUSTOM filter type
//         HistoricalDataRequest customRequest = HistoricalDataRequest.builder()
//                 .symbols(List.of("AAPL"))
//                 .fromDate(LocalDate.of(2023, 1, 1))
//                 .toDate(LocalDate.of(2023, 1, 31))
//                 .timeFrame(TimeFrame.DAY)
//                 .instrumentType(InstrumentType.STOCK)
//                 .filterType(FilterType.CUSTOM)
//                 .filterFrequency(5)
//                 .build();

//         marketDataApiClient.getHistoricalData(customRequest);
//         verify(requestHeadersUriSpec).uri(uriCaptor.capture());
//         String customUri = uriCaptor.getValue();
//         assertTrue(customUri.contains("filterFrequency=5"));

//         // Test with non-CUSTOM filter type
//         HistoricalDataRequest allRequest = HistoricalDataRequest.builder()
//                 .symbols(List.of("AAPL"))
//                 .fromDate(LocalDate.of(2023, 1, 1))
//                 .toDate(LocalDate.of(2023, 1, 31))
//                 .timeFrame(TimeFrame.DAY)
//                 .instrumentType(InstrumentType.STOCK)
//                 .filterType(FilterType.ALL)
//                 .filterFrequency(5) // This should be ignored
//                 .build();

//         marketDataApiClient.getHistoricalData(allRequest);
//         verify(requestHeadersUriSpec).uri(uriCaptor.capture());
//         String allUri = uriCaptor.getValue();
//         assertTrue(!allUri.contains("filterFrequency=5"));
//     }
// }
