package com.am.common.amcommondata.service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import com.am.common.amcommondata.document.security.SecurityDocument;
import com.am.common.amcommondata.mapper.SecurityModelMapper;
import com.am.common.amcommondata.model.security.SecurityModel;
import com.am.common.amcommondata.repository.security.SecurityRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class SecurityService {
    
    private static final Logger log = LoggerFactory.getLogger(SecurityService.class);
    
    private final SecurityRepository securityRepository;
    private final SecurityModelMapper securityMapper;
    private final AuditService auditService;
   

    public SecurityModel save(SecurityModel securityModel) {
        log.info("Saving security model: {}", securityModel.getKey());
        SecurityDocument document = securityMapper.toDocument(securityModel);
        auditService.updateAudit(document);
        SecurityModel result = securityMapper.toModel(securityRepository.save(document));
        log.debug("Successfully saved security with ID: {}", result.getId());
        return result;
    }

    public Optional<SecurityModel> findById(UUID id) {
        log.info("Finding security by ID: {}", id);
        Optional<SecurityModel> result = securityRepository.findById(id.toString())
                .map(securityMapper::toModel);
        log.debug("Found security by ID {}: {}", id, result.isPresent());
        return result;
    }

    public Optional<SecurityModel> findBySymbol(String symbol) {
        log.info("Finding security by symbol: {}", symbol);
        Optional<SecurityModel> result = securityRepository.findBySymbol(symbol).stream()
                .findFirst()
                .map(securityMapper::toModel);
        log.debug("Found security by symbol {}: {}", symbol, result.isPresent());
        return result;
    }

    public Optional<SecurityModel> findByKey(String key) {
        log.info("Finding security by key: {}", key);
        Optional<SecurityModel> result = securityRepository.findByKey(key).stream()
                .findFirst()
                .map(securityMapper::toModel);
        log.debug("Found security by key {}: {}", key, result.isPresent());
        return result;
    }

    public Optional<SecurityModel> findByIsin(String isin) {
        log.info("Finding security by ISIN: {}", isin);
        Optional<SecurityModel> result = securityRepository.findByIsin(isin).stream()
                .findFirst()
                .map(securityMapper::toModel);
        log.debug("Found security by ISIN {}: {}", isin, result.isPresent());
        return result;
    }

    public List<SecurityModel> findActiveLargeCapsByMinMarketCapAndSector(Long minMarketCap, String sector) {
        log.info("Finding active large caps by minimum market cap: {} and sector: {}", minMarketCap, sector);
        List<SecurityModel> results = securityRepository.findActiveLargeCapsByMinMarketCapAndSector(minMarketCap, sector)
                .stream()
                .map(securityMapper::toModel)
                .collect(Collectors.toList());
        log.debug("Found {} active large cap securities for sector: {} with minimum market cap: {}", results.size(), sector, minMarketCap);
        return results;
    }

    public List<SecurityModel> findAllVersionsById(UUID id) {
        log.info("Finding all versions of security by ID: {}", id);
        List<SecurityModel> results = securityRepository.findAllVersionsById(id.toString())
                .stream()
                .map(securityMapper::toModel)
                .collect(Collectors.toList());
        log.debug("Found {} versions of security with ID: {}", results.size(), id);
        return results;
    }

    public void deleteById(UUID id) {
        log.info("Deleting security by ID: {}", id);
        securityRepository.deleteById(id.toString());
        log.debug("Successfully deleted security with ID: {}", id);
    }

    public void deleteAll() {
        log.info("Deleting all securities");
        securityRepository.deleteAll();
        log.debug("Successfully deleted all securities");
    }

    public List<SecurityModel> saveAll(List<SecurityModel> securities) {
        log.info("Saving batch of {} securities", securities.size());
        List<SecurityDocument> documents = securities.stream()
                .map(securityMapper::toDocument)
                .peek(auditService::updateAudit)
                .collect(Collectors.toList());
        
        List<SecurityDocument> savedDocuments = new ArrayList<>();
        for (int i = 0; i < documents.size(); i += 100) {
            int end = Math.min(i + 100, documents.size());
            List<SecurityDocument> batch = documents.subList(i, end);
            log.debug("Saving batch of securities from index {} to {}", i, end - 1);
            savedDocuments.addAll(securityRepository.saveAll(batch));
        }
        
        List<SecurityModel> result = savedDocuments.stream()
                .map(securityMapper::toModel)
                .collect(Collectors.toList());
        log.debug("Successfully saved {} securities", result.size());
        return result;
    }
    
    /**
     * Find securities by a list of symbols, returning the latest version of each security based on audit creation time.
     * 
     * @param symbols List of security symbols to search for
     * @return List of SecurityModel objects, with the latest version of each security
     */
    /**
     * Find securities by a list of symbols, returning the latest version of each security based on audit creation time.
     * This method is designed to work reliably when used as a dependency in other services.
     * 
     * @param symbols List of security symbols to search for
     * @return List of SecurityModel objects, with the latest version of each security
     */
    public List<SecurityModel> findBySymbols(List<String> symbols) {
        log.info("Finding securities by symbols: {}", symbols);
        if (symbols == null || symbols.isEmpty()) {
            log.debug("Empty symbols list provided, returning empty result");
            return new ArrayList<>();
        }
        
        try {
            // Get all matching securities sorted by audit.createdAt in descending order
            log.debug("Querying repository for securities with symbols: {}", symbols);
            List<SecurityDocument> allSecurities = securityRepository.findBySymbols(symbols);
            
            if (allSecurities == null) {
                log.warn("Repository returned null for findBySymbols with symbols: {}", symbols);
                return new ArrayList<>();
            }
            
            log.debug("Found {} security documents for {} requested symbols", allSecurities.size(), symbols.size());
            
            // Group by symbol and take the first (latest) entry for each symbol
            Map<String, SecurityDocument> latestBySymbol = new HashMap<>();
            
            for (SecurityDocument doc : allSecurities) {
                if (doc != null && doc.getKey() != null && doc.getKey().getSymbol() != null) {
                    String symbol = doc.getKey().getSymbol();
                    if (!latestBySymbol.containsKey(symbol)) {
                        latestBySymbol.put(symbol, doc);
                    }
                } else {
                    log.warn("Encountered invalid security document: {}", doc);
                }
            }
            
            // Convert documents to models
            List<SecurityModel> result = latestBySymbol.values().stream()
                    .map(securityMapper::toModel)
                    .collect(Collectors.toList());
                    
            log.debug("Returning {} unique securities after filtering for latest versions", result.size());
            return result;
        } catch (Exception e) {
            log.error("Error finding securities by symbols: {}", symbols, e);
            return new ArrayList<>();
        }
    }
    
    /**
     * Enhanced search method that searches for securities based on a search parameter.
     * The search is performed across ISIN, symbol, and security name fields with prioritized results.
     * Results are ordered by exact match priority and then by name length for better relevance:
     * 1. Exact ISIN match (highest priority)
     * 2. Exact symbol match
     * 3. Exact security name match (case insensitive)
     * 4. Partial security name match (ordered by length)
     * 
     * Only active securities are returned.
     * 
     * @param searchParam The search parameter to look for
     * @return List of matching SecurityModel objects in priority order
     */
    public List<SecurityModel> findSecurityBySearchParam(String searchParam) {
        log.info("Finding securities by search parameter: {}", searchParam);
        if (searchParam == null || searchParam.trim().isEmpty()) {
            log.debug("Empty search parameter provided, returning empty result");
            return new ArrayList<>();
        }
        
        try {
            // Use the custom repository implementation for enhanced search
            List<SecurityDocument> matchingSecurities = securityRepository.findSecurityBySearchParam(searchParam);
            
            if (matchingSecurities == null) {
                log.warn("Repository returned null for findSecurityBySearchParam with parameter: {}", searchParam);
                return new ArrayList<>();
            }
            
            log.debug("Found {} matching securities for search parameter: {}", matchingSecurities.size(), searchParam);
            
            // Convert documents to models
            List<SecurityModel> result = matchingSecurities.stream()
                    .map(securityMapper::toModel)
                    .collect(Collectors.toList());
                    
            log.debug("Returning {} securities after search and prioritization", result.size());
            return result;
        } catch (Exception e) {
            log.error("Error finding securities by search parameter: {}", searchParam, e);
            return new ArrayList<>();
        }
    }
}
