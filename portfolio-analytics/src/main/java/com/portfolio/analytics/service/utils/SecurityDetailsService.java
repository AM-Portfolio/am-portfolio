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
    //@Cacheable(value = "securityDetails", key = "#symbols.toString()")
    public Map<String, SecurityModel> getSecurityDetails(List<String> symbols) {
        if (symbols == null || symbols.isEmpty()) {
            log.info("No symbols provided for security details lookup");
            return Collections.emptyMap();
        }
        
        log.info("Fetching security details for {} symbols", symbols.size());
        log.debug("Symbols to fetch: {}", symbols);
        
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
            
            log.info("Successfully retrieved {} security models out of {} requested symbols", 
                    resultMap.size(), symbols.size());
            
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
     * Gets a map of symbols to their sector names
     * 
     * @param symbols List of security symbols
     * @return Map of symbol to sector name
     */
    @Cacheable(value = "symbolSectors", key = "#symbols.toString()")
    public Map<String, String> getSymbolMapSectors(List<String> symbols) {
        Map<String, SecurityModel> securityDetails = getSecurityDetails(symbols);
        Map<String, String> sectorMap = new HashMap<>();
        
        securityDetails.forEach((symbol, security) -> {
            String sector = security != null && security.getMetadata() != null && security.getMetadata().getSector() != null ? 
                    security.getMetadata().getSector() : "Unknown";
            sectorMap.put(symbol, sector);
        });
        
        return sectorMap;
    }
    
    /**
     * Groups symbols by their sectors
     * 
     * @param symbols List of security symbols to group
     * @return Map of sectors to lists of symbols in each sector
     */
    //@Cacheable(value = "sectorGroupings", key = "#symbols.toString()")
    public Map<String, List<String>> groupSymbolsBySector(List<String> symbols) {
        if (symbols == null || symbols.isEmpty()) {
            log.info("No symbols provided for sector grouping");
            return Collections.emptyMap();
        }
        
        log.info("Grouping {} symbols by sector", symbols.size());
        Map<String, SecurityModel> securityDetails = getSecurityDetails(symbols);
        
        Map<String, List<String>> sectorToSymbols = new HashMap<>();
        
        securityDetails.forEach((symbol, securityModel) -> {
            String sector = securityModel.getMetadata().getSector();
            if (sector == null) {
                sector = "Unknown";
                log.debug("Symbol {} has no sector information, using 'Unknown'", symbol);
            }
            
            sectorToSymbols.computeIfAbsent(sector, k -> new ArrayList<>()).add(symbol);
        });
        
        log.info("Identified {} unique sectors across {} symbols", sectorToSymbols.size(), symbols.size());
        sectorToSymbols.forEach((sector, sectorSymbols) -> {
            log.debug("Sector '{}' contains {} symbols", sector, sectorSymbols.size());
        });
        
        return sectorToSymbols;
    }
    
    /**
     * Groups symbols by their industries
     * 
     * @param symbols List of security symbols to group
     * @return Map of industries to lists of symbols in each industry
     */
    //@Cacheable(value = "industryGroupings", key = "#symbols.toString()")
    public Map<String, List<String>> groupSymbolsByIndustry(List<String> symbols) {
        if (symbols == null || symbols.isEmpty()) {
            log.info("No symbols provided for industry grouping");
            return Collections.emptyMap();
        }
        
        log.info("Grouping {} symbols by industry", symbols.size());
        Map<String, SecurityModel> securityDetails = getSecurityDetails(symbols);
        
        Map<String, List<String>> industryToSymbols = new HashMap<>();
        
        securityDetails.forEach((symbol, securityModel) -> {
            String industry = securityModel.getMetadata().getIndustry();
            if (industry == null) {
                industry = "Unknown";
                log.debug("Symbol {} has no industry information, using 'Unknown'", symbol);
            }
            
            industryToSymbols.computeIfAbsent(industry, k -> new ArrayList<>()).add(symbol);
        });
        
        log.info("Identified {} unique industries across {} symbols", industryToSymbols.size(), symbols.size());
        industryToSymbols.forEach((industry, industrySymbols) -> {
            log.debug("Industry '{}' contains {} symbols", industry, industrySymbols.size());
        });
        
        return industryToSymbols;
    }
    
    /**
     * Groups symbols by their market type
     * 
     * @param symbols List of security symbols to group
     * @return Map of market types to lists of symbols in each market type
     */
    //@Cacheable(value = "marketTypeGroupings", key = "#symbols.toString()")
    public Map<String, List<String>> groupSymbolsByMarketType(List<String> symbols) {
        if (symbols == null || symbols.isEmpty()) {
            log.info("No symbols provided for market type grouping");
            return Collections.emptyMap();
        }
        
        log.info("Grouping {} symbols by market type", symbols.size());
        Map<String, SecurityModel> securityDetails = getSecurityDetails(symbols);
        
        Map<String, List<String>> marketTypeToSymbols = new HashMap<>();
        
        securityDetails.forEach((symbol, securityModel) -> {
            MarketCapType marketCapType = securityModel.getMetadata().getMarketCapType();
            String marketCapName = "UNKNOWN";
            
            if (marketCapType != null) {
                marketCapName = marketCapType.name();
            } else {
                log.debug("Symbol {} has no market cap type information, using 'UNKNOWN'", symbol);
            }
            
            marketTypeToSymbols.computeIfAbsent(marketCapName, k -> new ArrayList<>()).add(symbol);
        });
        
        log.info("Identified {} unique market cap types across {} symbols", marketTypeToSymbols.size(), symbols.size());
        marketTypeToSymbols.forEach((marketType, marketTypeSymbols) -> {
            log.debug("Market cap type '{}' contains {} symbols", marketType, marketTypeSymbols.size());
        });
        
        return marketTypeToSymbols;
    }
}
