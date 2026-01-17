package com.portfolio.basket.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.portfolio.basket.model.BasketOpportunity;
import com.portfolio.basket.model.BasketOpportunity.BasketItem;
import com.portfolio.basket.model.BasketOpportunity.ItemStatus;
import com.portfolio.model.portfolio.EquityHoldings;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.core.io.ClassPathResource;

import jakarta.annotation.PostConstruct;
import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;

@Service
@Slf4j
public class BasketEngineService {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private Map<String, EtfData> etfDataMap = new HashMap<>();

    @PostConstruct
    public void init() {
        // Load Mock Data for Phase 2 verification
        try {
            ClassPathResource resource = new ClassPathResource("mocks/etf_bulk_holdings.json");
            if (resource.exists()) {
                etfDataMap = objectMapper.readValue(resource.getInputStream(),
                        new TypeReference<Map<String, EtfData>>() {
                        });
                log.info("✅ Loaded {} ETFs from mock data", etfDataMap.size());
            } else {
                log.warn("⚠️ Mock data file not found in classpath");
            }
        } catch (IOException e) {
            log.error("❌ Failed to load mock ETF data", e);
        }
    }

    public List<BasketOpportunity> findOpportunities(List<EquityHoldings> userHoldings) {
        // 1. Index User Holdings for fast lookup
        // Map: ISIN -> Holding
        Map<String, EquityHoldings> userMap = userHoldings.stream()
                .collect(Collectors.toMap(EquityHoldings::getIsin, h -> h, (a, b) -> a));

        // Map: Sector -> List<Holding> (For Substitution)
        Map<String, List<EquityHoldings>> userSectorMap = userHoldings.stream()
                .filter(h -> h.getSector() != null)
                .collect(Collectors.groupingBy(EquityHoldings::getSector));

        List<BasketOpportunity> opportunities = new ArrayList<>();

        // 2. Iterate all ETFs
        for (Map.Entry<String, EtfData> entry : etfDataMap.entrySet()) {
            String etfIsin = entry.getKey();
            EtfData etf = entry.getValue();

            BasketOpportunity opportunity = calculateOverlap(etfIsin, etf, userMap, userSectorMap);
            if (opportunity.getMatchScore() >= 80.0) {
                opportunities.add(opportunity);
            }
        }

        return opportunities;
    }

    private BasketOpportunity calculateOverlap(String etfIsin, EtfData etf,
            Map<String, EquityHoldings> userMap,
            Map<String, List<EquityHoldings>> userSectorMap) {
        List<BasketItem> composition = new ArrayList<>();
        int matchCount = 0;
        int total = etf.getHoldings().size();

        for (EtfHolding req : etf.getHoldings()) {
            BasketItem item = BasketItem.builder()
                    .stockSymbol(req.getSymbol())
                    .isin(req.getIsin())
                    .sector(req.getSector())
                    .build();

            // A. Direct Match
            if (userMap.containsKey(req.getIsin())) {
                item.setStatus(ItemStatus.HELD);
                item.setUserHoldingSymbol(userMap.get(req.getIsin()).getSymbol());
                matchCount++;
            }
            // B. Sector Substitution
            else {
                List<EquityHoldings> sectorPeers = userSectorMap.getOrDefault(req.getSector(), Collections.emptyList());
                // Find a peer that is NOT already used? (Simple version: just take first peer)
                // Ideally we should track used peers to avoid double counting, but for v1
                // simple overlap is fine.
                // Improvement: check if any peer exists.
                if (!sectorPeers.isEmpty()) {
                    EquityHoldings substitute = sectorPeers.get(0);
                    item.setStatus(ItemStatus.SUBSTITUTE);
                    item.setUserHoldingSymbol(substitute.getSymbol());
                    item.setReason("Sector Match: " + req.getSector());
                    matchCount++; // Counts towards score!
                } else {
                    item.setStatus(ItemStatus.MISSING);
                }
            }
            composition.add(item);
        }

        double score = (total == 0) ? 0 : (double) matchCount / total * 100.0;

        return BasketOpportunity.builder()
                .etfIsin(etfIsin)
                .etfName(etf.getName())
                .matchScore(score)
                .totalItems(total)
                .heldCount(matchCount) // Includes substitutes
                .missingCount(total - matchCount)
                .composition(composition)
                .build();
    }

    // Internal Mock DTOs
    @Data
    public static class EtfData {
        private String symbol;
        private String name;
        private List<EtfHolding> holdings;
    }

    @Data
    public static class EtfHolding {
        private String isin;
        private String symbol;
        private String sector;
        private double weight;
    }
}
