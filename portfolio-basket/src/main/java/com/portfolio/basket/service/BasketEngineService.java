package com.portfolio.basket.service;

import com.portfolio.basket.client.EtfApiClient;
import com.portfolio.basket.engine.overlap.BasketOverlapCalculator;
import com.portfolio.basket.engine.sizing.BasketQuantityCalculator;
import com.portfolio.basket.engine.substitutes.BasketSubstituteApplier;
import com.portfolio.basket.exception.EtfNotFoundException;
import com.portfolio.basket.kernel.BasketPortfolioValueCalculator;
import com.portfolio.basket.kernel.BasketPriceResolver;
import com.portfolio.basket.kernel.HoldingsContext;
import com.portfolio.basket.model.BasketOpportunity;
import com.portfolio.basket.engine.overlap.SectorProfile;
import com.portfolio.basket.model.EtfData;
import com.portfolio.basket.model.EtfHolding;
import com.portfolio.basket.model.SubstituteAssignment;
import com.portfolio.basket.util.BasketUtils;
import com.portfolio.basket.util.SectorNormalizer;
import com.portfolio.model.portfolio.EquityHoldings;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@Slf4j
@RequiredArgsConstructor
public class BasketEngineService {

    private final EtfApiClient etfApiClient;
    private final EnrichedEtfService enrichedEtfService;
    private final BasketCatalogService basketCatalogService;
    private final BasketPriceResolver basketPriceResolver;
    private final BasketPortfolioValueCalculator basketPortfolioValueCalculator;
    private final BasketOverlapCalculator overlapCalculator;
    private final BasketSubstituteApplier substituteApplier;
    private final BasketQuantityCalculator quantityCalculator;

    public BasketOpportunity calculateBasketQuantities(Double investmentAmount, BasketOpportunity opportunity,
            boolean includeHeld, List<String> excludedSymbols) {
        return quantityCalculator.calculateBasketQuantities(investmentAmount, opportunity, includeHeld, excludedSymbols);
    }

    public List<BasketOpportunity> findOpportunities(List<EquityHoldings> userHoldings, String etfQuery) {
        BasketUtils.calculateUserWeights(userHoldings);

        double totalValue = userHoldings.stream()
                .mapToDouble(h -> {
                    if (h.getCurrentValue() != null) {
                        return h.getCurrentValue();
                    }
                    if (h.getInvestmentCost() != null) {
                        return h.getInvestmentCost();
                    }
                    return 0.0;
                })
                .sum();

        double remainingValue = userHoldings.stream()
                .mapToDouble(h -> {
                    double avail = h.getAvailableQuantity() != null ? h.getAvailableQuantity()
                            : (h.getQuantity() != null ? h.getQuantity() : 0.0);
                    double price = h.getCurrentPrice() != null ? h.getCurrentPrice() : 0.0;
                    return avail * price;
                })
                .sum();

        Set<String> allQueries = new LinkedHashSet<>();

        String effectiveQuery = (etfQuery == null || etfQuery.trim().isEmpty())
                ? basketCatalogService.resolveDefaultQuery()
                : etfQuery;
        if (effectiveQuery != null && !effectiveQuery.trim().isEmpty()) {
            if (effectiveQuery.contains(",")) {
                log.info("Processing query list: {}", effectiveQuery);
                for (String token : effectiveQuery.split(",")) {
                    String trimmed = token.trim();
                    if (trimmed.isEmpty()) {
                        continue;
                    }
                    resolveDiscoveryToken(trimmed, allQueries);
                }
            } else {
                log.info("Discovering ETFs via search query: {}", effectiveQuery);
                resolveDiscoveryToken(effectiveQuery.trim(), allQueries);
            }
        }

        if (allQueries.isEmpty()) {
            log.warn("No ETFs discovered for matching.");
            return Collections.emptyList();
        }

        log.info("Processing {} ETFs for matching", allQueries.size());
        List<BasketOpportunity> opportunities = findOpportunitiesInternal(userHoldings, allQueries);

        opportunities.forEach(op -> {
            op.setTotalPortfolioValue(totalValue);
            op.setRemainingPortfolioValue(remainingValue);
        });

        opportunities.sort(Comparator.comparingDouble(BasketOpportunity::getMatchScore).reversed());

        return opportunities;
    }

    private void resolveDiscoveryToken(String token, Set<String> out) {
        Map<String, String> aliases = basketCatalogService.preferredSymbolByAlias();
        String aliasHit = aliases.get(token.toLowerCase(Locale.ROOT));
        if (aliasHit != null && !aliasHit.isBlank()) {
            out.add(aliasHit);
            return;
        }
        if (isLikelyIsinOrSymbol(token)) {
            out.add(token);
            return;
        }
        List<String> found = etfApiClient.searchEtfs(token);
        if (found.isEmpty()) {
            out.add(token);
        } else {
            out.addAll(found);
        }
    }

    private boolean isLikelyIsinOrSymbol(String value) {
        if (value.contains(" ")) {
            return false;
        }
        if (value.matches("(?i)^INF[A-Z0-9]{10}$") || value.matches("(?i)^[A-Z]{2}[A-Z0-9]{10}$")) {
            return true;
        }
        return value.matches("^[A-Za-z0-9._-]+$") && value.length() <= 24;
    }

    private List<BasketOpportunity> findOpportunitiesInternal(List<EquityHoldings> userHoldings, Set<String> etfQueries) {
        Map<String, EquityHoldings> userMap = userHoldings.stream()
                .collect(Collectors.toMap(EquityHoldings::getIsin, h -> h, (a, b) -> a));

        Map<String, List<EquityHoldings>> userSectorMap = userHoldings.stream()
                .filter(h -> h.getSector() != null && !SectorNormalizer.isUnknown(h.getSector()))
                .filter(h -> h.getAvailableQuantity() == null || h.getAvailableQuantity() > 0)
                .collect(Collectors.groupingBy(h -> SectorNormalizer.normalizeFine(h.getSector())));

        List<BasketOpportunity> opportunities = new ArrayList<>();
        Map<String, EtfData> etfDataByInput = enrichedEtfService.getEnrichedEtfsBatch(new ArrayList<>(etfQueries));

        Set<String> symbolsToFetch = new HashSet<>();
        for (EquityHoldings h : userHoldings) {
            if (h.getSymbol() != null && !h.getSymbol().isBlank()) {
                symbolsToFetch.add(h.getSymbol());
            }
        }
        for (EtfData etf : etfDataByInput.values()) {
            if (etf == null || etf.getHoldings() == null) {
                continue;
            }
            for (EtfHolding holding : etf.getHoldings()) {
                if (holding.getSymbol() != null && !holding.getSymbol().isBlank()) {
                    symbolsToFetch.add(holding.getSymbol());
                }
            }
        }
        Map<String, Double> sharedPrices = basketPriceResolver.fetchPricesWithHoldingsFallback(symbolsToFetch, userHoldings);
        log.info("Opportunities shared price map size={} for {} ETF queries", sharedPrices.size(), etfQueries.size());

        for (String etfQuery : etfQueries) {
            EtfData etf = etfDataByInput.get(etfQuery);
            if (etf == null) {
                log.warn("No ETF resolved for query '{}' after batch lookup", etfQuery);
                continue;
            }

            SectorProfile sectorProfile = overlapCalculator.detectSectorProfile(etf);

            BasketOpportunity opportunity = overlapCalculator.calculateOverlap(
                    etfQuery, etf, userMap, userSectorMap, userHoldings, sectorProfile, sharedPrices);
            opportunities.add(opportunity);
        }

        return opportunities;
    }

    public EtfData getEtfData(String isin) {
        log.info("Fetching enriched ETF data for ISIN/symbol: {}", isin);
        EtfData data = enrichedEtfService.getEnrichedEtf(isin);
        if (data == null) {
            log.warn("⚠️ No ETF data available from API for ISIN: {}", isin);
        }
        return data;
    }

    public BasketOpportunity getPreview(String etfIsin, List<EquityHoldings> userHoldings) {
        BasketUtils.calculateUserWeights(userHoldings);

        EtfData etf = getEtfData(etfIsin);
        if (etf == null) {
            throw new EtfNotFoundException(
                    "ETF data not found for '" + etfIsin + "'. " +
                            "Use the ETF symbol (e.g. BANKBEES) or verify the ETF exists in the catalog."
            );
        }

        HoldingsContext ctx = HoldingsContext.from(userHoldings);
        SectorProfile sectorProfile = overlapCalculator.detectSectorProfile(etf);

        Set<String> etfSymbols = new HashSet<>();
        if (etf.getHoldings() != null) {
            for (EtfHolding h : etf.getHoldings()) {
                if (h.getSymbol() != null && !h.getSymbol().isBlank()) {
                    etfSymbols.add(h.getSymbol());
                }
            }
        }
        Set<String> symbolsToFetch = basketPriceResolver.unionSymbols(etfSymbols, userHoldings);
        Map<String, Double> prefetchedPrices =
                basketPriceResolver.fetchPricesWithHoldingsFallback(symbolsToFetch, userHoldings);

        BasketOpportunity opp = overlapCalculator.calculateOverlap(
                etfIsin, etf, ctx.getUserMap(), ctx.getUserSectorMap(), ctx.getAllUserHoldings(),
                sectorProfile, prefetchedPrices);

        BasketPortfolioValueCalculator.PortfolioValues values = basketPortfolioValueCalculator.calculate(userHoldings);
        opp.setTotalPortfolioValue(values.getTotalPortfolioValue());
        opp.setRemainingPortfolioValue(values.getRemainingPortfolioValue());

        return opp;
    }

    public BasketOpportunity applySubstitutesOnExisting(BasketOpportunity base, List<EquityHoldings> userHoldings,
            List<com.portfolio.basket.model.SubstituteAssignment> assignments) {
        return substituteApplier.applySubstitutesOnExisting(base, userHoldings, assignments);
    }

    /**
     * @deprecated use {@link SubstituteAssignment}
     */
    @Deprecated
    public static class SubstituteAssignment extends com.portfolio.basket.model.SubstituteAssignment {
        public SubstituteAssignment() {
            super();
        }

        public SubstituteAssignment(String missingIsin, String substituteIsin, Double assignedWeight) {
            super(missingIsin, substituteIsin, assignedWeight);
        }
    }
}
