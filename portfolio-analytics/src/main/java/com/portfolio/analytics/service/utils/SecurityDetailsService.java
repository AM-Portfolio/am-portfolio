package com.portfolio.analytics.service.utils;

import com.am.common.amcommondata.model.MarketCapType;
import com.am.common.amcommondata.model.security.SecurityModel;
import com.am.common.amcommondata.service.SecurityService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Utility service for retrieving security details like market cap, sector, and industry information
 * This service acts as a wrapper around SecurityService from am-common-data-service
 * and provides caching and fallback mechanisms
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class SecurityDetailsService {
    private final SecurityService securityService;
  
    /**
     * Retrieves security details for a list of symbols with caching and fallback mechanisms
     * 
     * @param symbols List of security symbols to retrieve details for
     * @return Map of symbols to their corresponding SecurityModel objects
     */
    @Cacheable(value = "securityDetails", key = "#symbols.toString()")
    public Map<String, SecurityModel> getSecurityDetails(List<String> symbols) {
        if (symbols == null || symbols.isEmpty()) {
            return Collections.emptyMap();
        }
        
        log.debug("Fetching security details for {} symbols", symbols.size());
        
        try {
            // Attempt to retrieve security details from the service
            List<SecurityModel> securityModels = securityService.findBySymbols(symbols);
            
            // Convert list to map using symbol as key
            Map<String, SecurityModel> resultMap = securityModels.stream()
                    .collect(Collectors.toMap(
                            securityModel -> securityModel.getKey().getSymbol(),
                            Function.identity(),    
                            (existing, replacement) -> existing
                    ));
            
            // Check for missing symbols and log them
            List<String> missingSymbols = new ArrayList<>(symbols);
            missingSymbols.removeAll(resultMap.keySet());
            
            if (!missingSymbols.isEmpty()) {
                log.warn("Could not find security details for symbols: {}", missingSymbols);
            }
            
            return resultMap;
        } catch (Exception e) {
            log.error("Error retrieving security details for symbols: {}", symbols, e);
            
            // Fallback: Try to retrieve symbols one by one to get partial results
            return retrieveWithFallback(symbols);
        }
    }


    
    /**
     * Fallback method to retrieve security details one by one when bulk retrieval fails
     * 
     * @param symbols List of security symbols to retrieve details for
     * @return Map of successfully retrieved symbols to their SecurityModel objects
     */
    private Map<String, SecurityModel> retrieveWithFallback(List<String> symbols) {
        log.info("Attempting fallback retrieval for {} symbols", symbols.size());
        Map<String, SecurityModel> resultMap = new HashMap<>();
        
        for (String symbol : symbols) {
            try {
                List<SecurityModel> models = securityService.findBySymbols(Collections.singletonList(symbol));
                if (!models.isEmpty()) {
                    resultMap.put(symbol, models.get(0));
                }
            } catch (Exception e) {
                log.warn("Failed to retrieve details for symbol: {}", symbol, e);
            }
        }
        
        log.info("Fallback retrieval completed. Retrieved {} out of {} symbols", resultMap.size(), symbols.size());
        return resultMap;
    }
    
    /**
     * Groups symbols by their sectors
     * 
     * @param symbols List of security symbols to group
     * @return Map of sectors to lists of symbols in each sector
     */
    @Cacheable(value = "sectorGroupings", key = "#symbols.toString()")
    public Map<String, List<String>> groupSymbolsBySector(List<String> symbols) {
        if (symbols == null || symbols.isEmpty()) {
            return Collections.emptyMap();
        }
        
        log.debug("Grouping {} symbols by sector", symbols.size());
        Map<String, SecurityModel> securityDetails = getSecurityDetails(symbols);
        
        Map<String, List<String>> sectorToSymbols = new HashMap<>();
        
        securityDetails.forEach((symbol, securityModel) -> {
            String sector = securityModel.getMetadata().getSector();
            if (sector == null) {
                sector = "Unknown";
            }
            
            sectorToSymbols.computeIfAbsent(sector, k -> new ArrayList<>()).add(symbol);
        });
        
        return sectorToSymbols;
    }
    
    /**
     * Groups symbols by their industries
     * 
     * @param symbols List of security symbols to group
     * @return Map of industries to lists of symbols in each industry
     */
    @Cacheable(value = "industryGroupings", key = "#symbols.toString()")
    public Map<String, List<String>> groupSymbolsByIndustry(List<String> symbols) {
        if (symbols == null || symbols.isEmpty()) {
            return Collections.emptyMap();
        }
        
        log.debug("Grouping {} symbols by industry", symbols.size());
        Map<String, SecurityModel> securityDetails = getSecurityDetails(symbols);
        
        Map<String, List<String>> industryToSymbols = new HashMap<>();
        
        securityDetails.forEach((symbol, securityModel) -> {
            String industry = securityModel.getMetadata().getIndustry();
            if (industry == null) {
                industry = "Unknown";
            }
            
            industryToSymbols.computeIfAbsent(industry, k -> new ArrayList<>()).add(symbol);
        });
        
        return industryToSymbols;
    }
    
    /**
     * Groups symbols by their market type
     * 
     * @param symbols List of security symbols to group
     * @return Map of market types to lists of symbols in each market type
     */
    @Cacheable(value = "marketTypeGroupings", key = "#symbols.toString()")
    public Map<String, List<String>> groupSymbolsByMarketType(List<String> symbols) {
        if (symbols == null || symbols.isEmpty()) {
            return Collections.emptyMap();
        }
        
        log.debug("Grouping {} symbols by market type", symbols.size());
        Map<String, SecurityModel> securityDetails = getSecurityDetails(symbols);
        
        Map<String, List<String>> marketTypeToSymbols = new HashMap<>();
        
        securityDetails.forEach((symbol, securityModel) -> {
            MarketCapType marketCapType = securityModel.getMetadata().getMarketCapType();
            if (marketCapType == null) {
                marketCapType = null;
            }
            
            marketTypeToSymbols.computeIfAbsent(marketCapType.name(), k -> new ArrayList<>()).add(symbol);
        });
        
        return marketTypeToSymbols;
    }
}
