package com.portfolio.analytics.service.utils;

import com.am.common.amcommondata.model.MarketCapType;
import com.am.common.amcommondata.model.security.SecurityKeyModel;
import com.am.common.amcommondata.model.security.SecurityMetadataModel;
import com.am.common.amcommondata.model.security.SecurityModel;
import com.am.common.amcommondata.service.SecurityService;
import com.am.common.amcommondata.service.marketcap.MarketCapMongoService;
import com.am.common.amcommondata.document.marketcap.MarketCapDocument;
import com.portfolio.marketdata.model.BatchSearchResponse;
import com.portfolio.marketdata.service.MarketDataService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
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
    private final com.portfolio.basket.client.EtfApiClient etfApiClient;
    private final MarketDataService marketDataService;
  
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

        long startMs = System.currentTimeMillis();
        log.info("Fetching security details for {} symbols", symbols.size());
        log.debug("Symbols to fetch: {}", symbols);

        try {
            List<SecurityModel> securityModels = securityService.findBySymbols(symbols);

            Map<String, SecurityModel> resultMap = new HashMap<>();
            if (securityModels != null) {
                for (SecurityModel model : securityModels) {
                    if (model != null && model.getKey() != null && model.getKey().getSymbol() != null) {
                        String sym = model.getKey().getSymbol();
                        boolean currentValid = !isMissingSector(
                                model.getMetadata() != null ? model.getMetadata().getSector() : null);
                        if (!resultMap.containsKey(sym) || currentValid) {
                            resultMap.put(sym, model);
                        }
                    }
                }
            }
            int securityHits = resultMap.size();

            List<String> missingOrIncomplete = symbols.stream()
                    .filter(s -> isMissingSectorMeta(resultMap.get(s)))
                    .collect(Collectors.toList());

            int mongoMcHits = 0;
            if (!missingOrIncomplete.isEmpty() && marketCapMongoService != null) {
                try {
                    Map<String, MarketCapDocument> marketCapDocs = marketCapMongoService.getBySymbols(missingOrIncomplete);
                    if (marketCapDocs != null && !marketCapDocs.isEmpty()) {
                        mongoMcHits = marketCapDocs.size();
                        for (Map.Entry<String, MarketCapDocument> entry : marketCapDocs.entrySet()) {
                            applyMarketCapDocument(resultMap, entry.getKey(), entry.getValue());
                        }
                    }
                } catch (Exception e) {
                    log.warn("Error fetching fallback market cap details for symbols: {}", missingOrIncomplete, e);
                }
            }

            List<String> stillMissing = symbols.stream()
                    .filter(s -> isMissingSectorMeta(resultMap.get(s)))
                    .collect(Collectors.toList());

            int apiHits = 0;
            if (!stillMissing.isEmpty() && marketDataService != null) {
                try {
                    Map<String, BatchSearchResponse.SecurityMatch> apiMatches =
                            marketDataService.getMarketCapData(stillMissing);
                    if (apiMatches != null && !apiMatches.isEmpty()) {
                        apiHits = apiMatches.size();
                        List<MarketCapDocument> toPersist = new ArrayList<>();
                        for (Map.Entry<String, BatchSearchResponse.SecurityMatch> entry : apiMatches.entrySet()) {
                            String sym = entry.getKey();
                            BatchSearchResponse.SecurityMatch match = entry.getValue();
                            if (match == null) {
                                continue;
                            }
                            applySecurityMatch(resultMap, sym, match);
                            toPersist.add(MarketCapDocument.builder()
                                    .symbol(sym)
                                    .sector(match.getSector())
                                    .industry(match.getIndustry())
                                    .marketCapType(match.getMarketCapType())
                                    .marketCapValue(match.getMarketCapValue() != null
                                            ? match.getMarketCapValue().doubleValue() : null)
                                    .companyName(match.getCompanyName())
                                    .updatedAt(LocalDateTime.now())
                                    .build());
                        }
                        if (!toPersist.isEmpty() && marketCapMongoService != null) {
                            try {
                                marketCapMongoService.saveAll(toPersist);
                            } catch (Exception e) {
                                log.warn("Failed to write-through market_cap docs for {} symbols: {}",
                                        toPersist.size(), e.getMessage());
                            }
                        }
                    }
                } catch (Exception e) {
                    log.warn("Error fetching market-data fallback for symbols: {}", stillMissing, e);
                }
            }

            int unknownSector = 0;
            for (String s : symbols) {
                if (isMissingSectorMeta(resultMap.get(s))) {
                    unknownSector++;
                }
            }

            log.info(
                    "SecurityDetails coverage symbols={} securityHits={} mongoMc={} apiHits={} unknownSector={} durationMs={}",
                    symbols.size(), securityHits, mongoMcHits, apiHits, unknownSector,
                    System.currentTimeMillis() - startMs);

            List<String> missingSymbols = new ArrayList<>(symbols);
            missingSymbols.removeAll(resultMap.keySet());
            if (!missingSymbols.isEmpty()) {
                log.warn("Could not find security details for symbols: {}", missingSymbols);
            }

            return resultMap;
        } catch (Exception e) {
            log.error("Error retrieving security details for symbols: {}", symbols, e);
            return retrieveWithFallback(symbols);
        }
    }

    private void applyMarketCapDocument(Map<String, SecurityModel> resultMap, String sym, MarketCapDocument doc) {
        if (doc == null) {
            return;
        }
        SecurityModel existing = resultMap.get(sym);
        MarketCapType mcType = parseMarketCapType(doc.getMarketCapType());
        if (existing == null) {
            SecurityMetadataModel meta = SecurityMetadataModel.builder()
                    .securityName(doc.getCompanyName())
                    .sector(doc.getSector())
                    .industry(doc.getIndustry())
                    .marketCapType(mcType)
                    .marketCapValue(doc.getMarketCapValue())
                    .build();
            resultMap.put(sym, SecurityModel.builder()
                    .key(SecurityKeyModel.builder().symbol(sym).build())
                    .metadata(meta)
                    .build());
            return;
        }
        if (existing.getMetadata() == null) {
            existing.setMetadata(SecurityMetadataModel.builder().build());
        }
        SecurityMetadataModel meta = existing.getMetadata();
        if (isMissingSector(meta.getSector()) && doc.getSector() != null) {
            meta.setSector(doc.getSector());
        }
        if (isMissingSector(meta.getIndustry()) && doc.getIndustry() != null) {
            meta.setIndustry(doc.getIndustry());
        }
        if (meta.getMarketCapType() == null && mcType != null) {
            meta.setMarketCapType(mcType);
        }
        if (meta.getMarketCapValue() == null && doc.getMarketCapValue() != null) {
            meta.setMarketCapValue(doc.getMarketCapValue());
        }
        if (meta.getSecurityName() == null && doc.getCompanyName() != null) {
            meta.setSecurityName(doc.getCompanyName());
        }
    }

    private void applySecurityMatch(
            Map<String, SecurityModel> resultMap, String sym, BatchSearchResponse.SecurityMatch match) {
        MarketCapType mcType = parseMarketCapType(match.getMarketCapType());
        SecurityModel existing = resultMap.get(sym);
        if (existing == null) {
            SecurityMetadataModel meta = SecurityMetadataModel.builder()
                    .securityName(match.getCompanyName())
                    .sector(match.getSector())
                    .industry(match.getIndustry())
                    .marketCapType(mcType)
                    .marketCapValue(match.getMarketCapValue() != null
                            ? match.getMarketCapValue().doubleValue() : null)
                    .build();
            resultMap.put(sym, SecurityModel.builder()
                    .key(SecurityKeyModel.builder().symbol(sym).build())
                    .metadata(meta)
                    .build());
            return;
        }
        if (existing.getMetadata() == null) {
            existing.setMetadata(SecurityMetadataModel.builder().build());
        }
        SecurityMetadataModel meta = existing.getMetadata();
        if (isMissingSector(meta.getSector()) && match.getSector() != null) {
            meta.setSector(match.getSector());
        }
        if (isMissingSector(meta.getIndustry()) && match.getIndustry() != null) {
            meta.setIndustry(match.getIndustry());
        }
        if (meta.getMarketCapType() == null && mcType != null) {
            meta.setMarketCapType(mcType);
        }
        if (meta.getMarketCapValue() == null && match.getMarketCapValue() != null) {
            meta.setMarketCapValue(match.getMarketCapValue().doubleValue());
        }
        if (meta.getSecurityName() == null && match.getCompanyName() != null) {
            meta.setSecurityName(match.getCompanyName());
        }
    }

    static boolean isMissingSector(String sector) {
        if (sector == null) {
            return true;
        }
        String t = sector.trim();
        return t.isEmpty() || "-".equals(t) || "Unknown".equalsIgnoreCase(t);
    }

    private static boolean isMissingSectorMeta(SecurityModel sm) {
        if (sm == null || sm.getMetadata() == null) {
            return true;
        }
        return isMissingSector(sm.getMetadata().getSector());
    }

    /**
     * Parse labeled cap type. Null/blank/unrecognized → null (never invent MICRO_CAP).
     */
    MarketCapType parseMarketCapType(String typeStr) {
        return MarketCapType.fromMarketCap(typeStr);
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
    
    public String resolveSector(String symbol, String rawSector) {
        if (rawSector != null && !rawSector.trim().isEmpty() && !rawSector.trim().equals("-") && !rawSector.trim().equalsIgnoreCase("Unknown")) {
            return rawSector.trim();
        }
        if (etfApiClient.isEtf(symbol)) {
            return "Exchange Traded Funds (ETFs)";
        }
        return "Unknown";
    }

    public String resolveIndustry(String symbol, String rawIndustry) {
        if (rawIndustry != null && !rawIndustry.trim().isEmpty() && !rawIndustry.trim().equals("-") && !rawIndustry.trim().equalsIgnoreCase("Unknown")) {
            return rawIndustry.trim();
        }
        if (etfApiClient.isEtf(symbol)) {
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
        return groupSymbolsBySector(symbols, securityDetails);
    }
    
    public Map<String, List<String>> groupSymbolsBySector(List<String> symbols, Map<String, SecurityModel> securityDetails) {
        if (symbols == null || symbols.isEmpty()) {
            return Collections.emptyMap();
        }
        
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
        return groupSymbolsByIndustry(symbols, securityDetails);
    }
    
    public Map<String, List<String>> groupSymbolsByIndustry(List<String> symbols, Map<String, SecurityModel> securityDetails) {
        if (symbols == null || symbols.isEmpty()) {
            return Collections.emptyMap();
        }
        
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
        
        log.info("Grouping {} symbols by market cap type", symbols.size());
        Map<String, SecurityModel> securityDetails = getSecurityDetails(symbols);
        return groupSymbolsByMarketType(symbols, securityDetails);
    }
    
    public Map<String, List<String>> groupSymbolsByMarketType(List<String> symbols, Map<String, SecurityModel> securityDetails) {
        if (symbols == null || symbols.isEmpty()) {
            return Collections.emptyMap();
        }
        
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
