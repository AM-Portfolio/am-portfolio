package com.portfolio.marketdata.service;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;

import com.portfolio.marketdata.client.NseIndicesApiClient;
import com.portfolio.marketdata.model.indices.IndexConstituent;
import com.portfolio.marketdata.model.indices.IndexData;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Mono;

/**
 * Service for interacting with NSE Indices data.
 * This service follows the Single Responsibility Principle by focusing only on NSE Indices operations.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class NseIndicesService {

    private final NseIndicesApiClient nseIndicesApiClient;
    
    /**
     * Gets the index data for the specified index symbol asynchronously.
     * 
     * @param indexSymbol the index symbol
     * @return a Mono of IndexData
     */
    public Mono<IndexData> getIndexData(String indexSymbol) {
        log.info("Getting index data for {}", indexSymbol);
        return nseIndicesApiClient.getIndexData(indexSymbol);
    }
    
    /**
     * Gets the index data for the specified index symbol synchronously.
     * 
     * @param indexSymbol the index symbol
     * @return the IndexData
     */
    public IndexData getIndexDataSync(String indexSymbol) {
        log.info("Getting index data synchronously for {}", indexSymbol);
        return nseIndicesApiClient.getIndexDataSync(indexSymbol);
    }
    
    /**
     * Gets the constituents of the specified index.
     * 
     * @param indexSymbol the index symbol
     * @return a list of IndexConstituent
     */
    public List<IndexConstituent> getIndexConstituents(String indexSymbol) {
        log.info("Getting constituents for index {}", indexSymbol);
        IndexData indexData = getIndexDataSync(indexSymbol);
        return indexData != null ? indexData.getData() : List.of();
    }
    
    /**
     * Gets the industry distribution of the specified index.
     * 
     * @param indexSymbol the index symbol
     * @return a map of industry to count of constituents
     */
    public Map<String, Long> getIndustryDistribution(String indexSymbol) {
        log.info("Getting industry distribution for index {}", indexSymbol);
        List<IndexConstituent> constituents = getIndexConstituents(indexSymbol);
        
        return constituents.stream()
                .filter(constituent -> constituent.getIndustry() != null && !constituent.getIndustry().isEmpty())
                .collect(Collectors.groupingBy(
                        IndexConstituent::getIndustry,
                        Collectors.counting()
                ));
    }
    
    /**
     * Gets the symbols of all constituents in the specified index.
     * 
     * @param indexSymbol the index symbol
     * @return a list of constituent symbols
     */
    public List<String> getConstituentSymbols(String indexSymbol) {
        log.info("Getting constituent symbols for index {}", indexSymbol);
        return getIndexConstituents(indexSymbol).stream()
                .map(IndexConstituent::getSymbol)
                .collect(Collectors.toList());
    }
}
