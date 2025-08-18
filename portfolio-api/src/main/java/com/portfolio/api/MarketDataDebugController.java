package com.portfolio.api;

import java.util.Arrays;
import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.reactive.function.client.WebClient;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Mono;

/**
 * Debug controller for market data API
 */
@RestController
@RequestMapping("/api/v1/debug")
@RequiredArgsConstructor
@Slf4j
public class MarketDataDebugController {

    private final WebClient.Builder webClientBuilder;
    
    /**
     * Get raw market data response for debugging
     * @param symbols The symbols to fetch data for
     * @return Raw API response
     */
    @GetMapping("/market-data/raw")
    public ResponseEntity<String> getRawMarketData(
            @RequestParam(defaultValue = "NIFTY 50,INFY,TCS") String symbols) {
        
        List<String> symbolsList = Arrays.asList(symbols.split(","));
        String symbolsParam = String.join(",", symbolsList);
        String path = "/api/v1/market-data/ohlc?symbols=" + symbolsParam;
        
        log.info("Fetching raw market data for symbols: {}", symbolsParam);
        
        String response = webClientBuilder.build()
                .get()
                .uri("http://localhost:8084" + path)
                .retrieve()
                .bodyToMono(String.class)
                .block();
        
        log.info("Raw API response: {}", response);
        return ResponseEntity.ok(response);
    }
    
    /**
     * Get raw NSE indices response for debugging
     * @param indexSymbol The index symbol
     * @return Raw API response
     */
    @GetMapping("/nse-indices/raw")
    public ResponseEntity<String> getRawNseIndicesData(
            @RequestParam(defaultValue = "NIFTY50") String indexSymbol) {
        
        String path = "/api/v1/nse-indices/" + indexSymbol;
        
        log.info("Fetching raw NSE indices data for: {}", indexSymbol);
        
        String response = webClientBuilder.build()
                .get()
                .uri("http://localhost:8084" + path)
                .retrieve()
                .bodyToMono(String.class)
                .block();
        
        log.info("Raw API response: {}", response);
        return ResponseEntity.ok(response);
    }
}
