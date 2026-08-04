package com.portfolio.analytics.service.utils;

import com.am.common.amcommondata.model.MarketCapType;
import com.am.common.amcommondata.model.security.SecurityKeyModel;
import com.am.common.amcommondata.model.security.SecurityMetadataModel;
import com.am.common.amcommondata.model.security.SecurityModel;
import com.am.common.amcommondata.service.SecurityService;
import com.am.common.amcommondata.service.marketcap.MarketCapMongoService;
import com.am.common.amcommondata.document.marketcap.MarketCapDocument;
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
    private final MarketCapMongoService marketCapMongoService;
  
    /**
     * Retrieves security details for a list of symbols with caching and fallback mechanisms
     * 
     * @param symbols List of security symbols to retrieve details for
     * @return Map of symbols to their corresponding SecurityModel objects
     */
    @Cacheable(value = "securityDetails", key = "#symbols.toString()")
    public Map<String, SecurityModel> getSecurityDetails(List<String> symbols) {
        if (symbols == null || symbols.isEmpty()) {
            log.info("No symbols provided for security details lookup");
            return Collections.emptyMap();
        }
        
        log.info("Fetching security details for {} symbols", symbols.size());
        log.debug("Symbols to fetch: {}", symbols);
        
        try {
            // Attempt to retrieve security details from the service using the raw symbols
            List<SecurityModel> securityModels = securityService.findBySymbols(symbols);
            
            // Convert list to map using the exact symbol as key
            Map<String, SecurityModel> resultMap = new HashMap<>();
            if (securityModels != null) {
                for (SecurityModel model : securityModels) {
                    if (model != null && model.getKey() != null && model.getKey().getSymbol() != null) {
                        String sym = model.getKey().getSymbol();
                        boolean currentValid = model.getMetadata() != null
                                && model.getMetadata().getSector() != null
                                && !model.getMetadata().getSector().trim().isEmpty()
                                && !model.getMetadata().getSector().trim().equals("-");
                        if (!resultMap.containsKey(sym) || currentValid) {
                            resultMap.put(sym, model);
                        }
                    }
                }
            }

            // Identify symbols that are missing or have missing/invalid sector metadata
            List<String> missingOrIncomplete = symbols.stream()
                    .filter(s -> {
                        SecurityModel sm = resultMap.get(s);
                        return sm == null || sm.getMetadata() == null || sm.getMetadata().getSector() == null
                                || sm.getMetadata().getSector().trim().isEmpty()
                                || sm.getMetadata().getSector().trim().equals("-");
                    })
                    .collect(Collectors.toList());

            if (!missingOrIncomplete.isEmpty() && marketCapMongoService != null) {
                try {
                    Map<String, MarketCapDocument> marketCapDocs = marketCapMongoService.getBySymbols(missingOrIncomplete);
                    if (marketCapDocs != null && !marketCapDocs.isEmpty()) {
                        for (Map.Entry<String, MarketCapDocument> entry : marketCapDocs.entrySet()) {
                            String sym = entry.getKey();
                            MarketCapDocument doc = entry.getValue();
                            if (doc != null) {
                                SecurityModel existing = resultMap.get(sym);
                                if (existing == null) {
                                    MarketCapType mcType = parseMarketCapType(doc.getMarketCapType());
                                    SecurityMetadataModel meta = SecurityMetadataModel.builder()
                                            .securityName(doc.getCompanyName())
                                            .sector(doc.getSector())
                                            .industry(doc.getIndustry())
                                            .marketCapType(mcType)
                                            .marketCapValue(doc.getMarketCapValue())
                                            .build();
                                    SecurityModel synthetic = SecurityModel.builder()
                                            .key(SecurityKeyModel.builder().symbol(sym).build())
                                            .metadata(meta)
                                            .build();
                                    resultMap.put(sym, synthetic);
                                } else {
                                    if (existing.getMetadata() == null) {
                                        existing.setMetadata(SecurityMetadataModel.builder().build());
                                    }
                                    SecurityMetadataModel meta = existing.getMetadata();
                                    if ((meta.getSector() == null || meta.getSector().trim().isEmpty() || meta.getSector().trim().equals("-")) && doc.getSector() != null) {
                                        meta.setSector(doc.getSector());
                                    }
                                    if ((meta.getIndustry() == null || meta.getIndustry().trim().isEmpty() || meta.getIndustry().trim().equals("-")) && doc.getIndustry() != null) {
                                        meta.setIndustry(doc.getIndustry());
                                    }
                                    if (meta.getMarketCapType() == null && doc.getMarketCapType() != null) {
                                        meta.setMarketCapType(parseMarketCapType(doc.getMarketCapType()));
                                    }
                                    if (meta.getMarketCapValue() == null && doc.getMarketCapValue() != null) {
                                        meta.setMarketCapValue(doc.getMarketCapValue());
                                    }
                                    if (meta.getSecurityName() == null && doc.getCompanyName() != null) {
                                        meta.setSecurityName(doc.getCompanyName());
                                    }
                                }
                            }
                        }
                    }
                } catch (Exception e) {
                    log.warn("Error fetching fallback market cap details for symbols: {}", missingOrIncomplete, e);
                }
            }
            
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

    private MarketCapType parseMarketCapType(String typeStr) {
        if (typeStr == null || typeStr.trim().isEmpty()) {
            return MarketCapType.MICRO_CAP;
        }
        String normalized = typeStr.trim().toUpperCase().replace(" ", "_");
        try {
            return MarketCapType.valueOf(normalized);
        } catch (Exception e) {
            return MarketCapType.MICRO_CAP;
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
        
        for (String rawSymbol : symbols) {
            try {
                List<SecurityModel> models = securityService.findBySymbols(Collections.singletonList(rawSymbol));
                if (!models.isEmpty()) {
                    resultMap.put(rawSymbol, models.get(0));
                }
            } catch (Exception e) {
                log.warn("Failed to retrieve details for symbol: {}", rawSymbol, e);
            }
        }
        
        log.info("Fallback retrieval completed. Retrieved {} out of {} symbols", resultMap.size(), symbols.size());
        return resultMap;
    }
    
    private static final java.util.regex.Pattern ETF_PATTERN = java.util.regex.Pattern.compile(
        ".*(BEES|IETF|ETF|INDEX|INVIT|REIT)$|^(MON100|MAFANG|SMALLCAP|MID150|GOLDBEES|SILVERBEES|JUNIORBEES|LIQUIDBEES|NIFTYBEES|BANKBEES|AUTOBEES|PHARMABEES|CPSEETF|ITBEES|METALIETF|HDFCSML250|MOM100|MOM30|ALPHA50).*",
        java.util.regex.Pattern.CASE_INSENSITIVE
    );

    public static boolean isEtf(String symbol) {
        if (symbol == null) return false;
        String clean = symbol.trim().toUpperCase();
        return ETF_PATTERN.matcher(clean).matches();
    }

    public static String resolveSector(String symbol, String rawSector) {
        if (rawSector != null && !rawSector.trim().isEmpty() && !rawSector.trim().equals("-") && !rawSector.trim().equalsIgnoreCase("Unknown")) {
            return rawSector.trim();
        }
        if (isEtf(symbol)) {
            return "Exchange Traded Funds (ETFs)";
        }
        return "Unknown";
    }

    public static String resolveIndustry(String symbol, String rawIndustry) {
        if (rawIndustry != null && !rawIndustry.trim().isEmpty() && !rawIndustry.trim().equals("-") && !rawIndustry.trim().equalsIgnoreCase("Unknown")) {
            return rawIndustry.trim();
        }
        if (isEtf(symbol)) {
            return "ETFs & Index Funds";
        }
        return "Unknown";
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
        
        for (String symbol : symbols) {
            SecurityModel security = securityDetails.get(symbol);
            String rawSector = (security != null && security.getMetadata() != null && security.getMetadata().getSector() != null) ? 
                    security.getMetadata().getSector() : null;
            String sector = resolveSector(symbol, rawSector);
            sectorMap.put(symbol, sector);
        }
        
        return sectorMap;
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
            log.info("No symbols provided for sector grouping");
            return Collections.emptyMap();
        }
        
        log.info("Grouping {} symbols by sector", symbols.size());
        Map<String, SecurityModel> securityDetails = getSecurityDetails(symbols);
        
        Map<String, List<String>> sectorToSymbols = new HashMap<>();
        
        for (String symbol : symbols) {
            SecurityModel securityModel = securityDetails.get(symbol);
            String rawSector = (securityModel != null && securityModel.getMetadata() != null)
                ? securityModel.getMetadata().getSector() : null;
            String sector = resolveSector(symbol, rawSector);
            
            sectorToSymbols.computeIfAbsent(sector, k -> new ArrayList<>()).add(symbol);
        }
        
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
    @Cacheable(value = "industryGroupings", key = "#symbols.toString()")
    public Map<String, List<String>> groupSymbolsByIndustry(List<String> symbols) {
        if (symbols == null || symbols.isEmpty()) {
            log.info("No symbols provided for industry grouping");
            return Collections.emptyMap();
        }
        
        log.info("Grouping {} symbols by industry", symbols.size());
        Map<String, SecurityModel> securityDetails = getSecurityDetails(symbols);
        
        Map<String, List<String>> industryToSymbols = new HashMap<>();
        
        for (String symbol : symbols) {
            SecurityModel securityModel = securityDetails.get(symbol);
            String rawIndustry = (securityModel != null && securityModel.getMetadata() != null)
                ? securityModel.getMetadata().getIndustry() : null;
            String industry = resolveIndustry(symbol, rawIndustry);
            
            industryToSymbols.computeIfAbsent(industry, k -> new ArrayList<>()).add(symbol);
        }
        
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
    @Cacheable(value = "marketTypeGroupings", key = "#symbols.toString()")
    public Map<String, List<String>> groupSymbolsByMarketType(List<String> symbols) {
        if (symbols == null || symbols.isEmpty()) {
            log.info("No symbols provided for market type grouping");
            return Collections.emptyMap();
        }
        
        log.info("Grouping {} symbols by market type", symbols.size());
        Map<String, SecurityModel> securityDetails = getSecurityDetails(symbols);
        
        Map<String, List<String>> marketTypeToSymbols = new HashMap<>();
        
        for (String symbol : symbols) {
            SecurityModel securityModel = securityDetails.get(symbol);
            String marketCapName = "UNKNOWN";
            
            if (securityModel != null && securityModel.getMetadata() != null && securityModel.getMetadata().getMarketCapType() != null) {
                marketCapName = securityModel.getMetadata().getMarketCapType().name();
            } else {
                log.debug("Symbol {} has no market cap type information, using 'UNKNOWN'", symbol);
            }
            
            marketTypeToSymbols.computeIfAbsent(marketCapName, k -> new ArrayList<>()).add(symbol);
        }
        
        log.info("Identified {} unique market cap types across {} symbols", marketTypeToSymbols.size(), symbols.size());
        marketTypeToSymbols.forEach((marketType, marketTypeSymbols) -> {
            log.debug("Market cap type '{}' contains {} symbols", marketType, marketTypeSymbols.size());
        });
        
        return marketTypeToSymbols;
    }
}
