package com.portfolio.analytics.service.providers.portfolio;

import com.am.common.amcommondata.model.PortfolioModelV1;
import com.am.common.amcommondata.model.asset.equity.EquityModel;
import com.am.common.amcommondata.service.PortfolioService;
import com.portfolio.analytics.model.AnalyticsType;
import com.portfolio.analytics.service.utils.AnalyticsUtils;
import com.portfolio.analytics.service.utils.HeatmapUtils;
import com.portfolio.analytics.service.utils.SecurityDetailsService;
import com.portfolio.marketdata.service.MarketDataService;
import com.portfolio.marketdata.service.MutualFundNavService;
import com.portfolio.marketdata.model.MutualFundNavMark;
import com.portfolio.model.analytics.Heatmap;
import com.portfolio.model.analytics.request.AdvancedAnalyticsRequest;
import com.portfolio.model.market.MarketData;
import com.portfolio.model.portfolio.HoldingInstrumentClassifier;
import com.portfolio.model.portfolio.HoldingMarkPriceResolver;
import com.portfolio.model.portfolio.mf.MutualFundSchemeCategory;
import com.portfolio.model.portfolio.mf.MutualFundSchemeCategoryResolver;
import com.portfolio.model.resolver.TradingSymbolResolver;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.*;
import java.util.Locale;
import java.util.stream.Collectors;

import com.portfolio.redis.service.PortfolioHeatmapRedisService;

/**
 * Provider for portfolio sector heatmap analytics
 */
@Service
@Slf4j
public class PortfolioHeatmapProvider extends AbstractPortfolioAnalyticsProvider<Heatmap> {

    @org.springframework.lang.Nullable
    private final PortfolioHeatmapRedisService heatmapRedisService;
    private final MutualFundNavService mutualFundNavService;

    public PortfolioHeatmapProvider(PortfolioService portfolioService, MarketDataService marketDataService, SecurityDetailsService securityDetailsService, @org.springframework.lang.Nullable PortfolioHeatmapRedisService heatmapRedisService, MutualFundNavService mutualFundNavService) {
        super(portfolioService, marketDataService, securityDetailsService);
        this.heatmapRedisService = heatmapRedisService;
        this.mutualFundNavService = mutualFundNavService;
    }

    @Override
    public AnalyticsType getType() {
        return AnalyticsType.SECTOR_HEATMAP;
    }

    @Override
    public Heatmap generateAnalytics(AdvancedAnalyticsRequest request) {
        log.info("Generating sector heatmap for portfolio: {}", request.getCoreIdentifiers().getPortfolioId());
        
        String portfolioId = request.getCoreIdentifiers().getPortfolioId();
        applyAssetScopeToPrefetch(request);

        // Live/1D: never serve Redis mosaic (cash or closed) — stale after-close tiles freeze wrong day%.
        boolean skipHeatmapCacheRead = shouldApplyTodayChangeOverride(request);
        if (heatmapRedisService != null && !skipHeatmapCacheRead) {
            Optional<Heatmap> cached = heatmapRedisService.getCachedHeatmap(portfolioId, request);
            if (cached.isPresent()) {
                return cached.get();
            }
        }

        return processPortfolioData(
            portfolioId,
            request,
            this::createEmptyResult,
            (portfolio, portfolioSymbols, marketData) -> {
                portfolio = filterPortfolioByAssetScope(portfolio, request != null ? request.getAssetScope() : null);
                if (portfolio.getEquityModels() == null || portfolio.getEquityModels().isEmpty()) {
                    return createEmptyResult();
                }
                // Poisoned scheme tickers (RECLTD × many INF*) → ISIN book keys first.
                applyIsinBookKeys(portfolio);
                // ISIN-as-symbol (INF… ETF / unlisted) → broker display ticker for mosaic labels.
                applyDisplayTickers(portfolio);
                List<String> displaySymbols = getPortfolioSymbols(portfolio);
                Map<String, MarketData> displayMarketData = remappedMarketData(marketData, portfolio);

                // Create a map of symbol to holding quantity
                Map<String, Double> symbolToQuantity = createSymbolToQuantityMap(portfolio);

                Map<String, EquityModel> equityBySymbol = indexEquities(portfolio);
                String assetScope = normalizeAssetScope(request != null ? request.getAssetScope() : null);
                boolean mfScope = "MUTUAL_FUND".equals(assetScope);

                // MF scope sectors come from schemeClass/name — skip security-details (large cold cost).
                Map<String, List<String>> sectorToStocks;
                if (mfScope) {
                    sectorToStocks = new HashMap<>();
                } else {
                    Map<String, com.am.common.amcommondata.model.security.SecurityModel> securityDetails =
                            getSecurityDetails(displaySymbols, request);
                    sectorToStocks = securityDetailsService.groupSymbolsBySector(displaySymbols, securityDetails);
                }
                List<String> mfIsins = portfolio.getEquityModels() == null ? List.of() : portfolio.getEquityModels().stream()
                        .filter(Objects::nonNull)
                        .filter(e -> HoldingInstrumentClassifier.quarantineEquityLtp(
                                e.getAssetClass(), e.getSymbol(), e.getIsin(), e.getName()))
                        .map(e -> e.getIsin() != null && !e.getIsin().isBlank() ? e.getIsin() : e.getSymbol())
                        .filter(HoldingInstrumentClassifier::isSchemeMfIsin)
                        .distinct()
                        .toList();
                Map<String, MutualFundNavMark> mfMarks = mutualFundNavService.getMarks(mfIsins);
                // Equity holding sector + instrument classifier fills Unknown / blank meta.
                // MUTUAL_FUND scope: place each scheme under its SEBI category (not one "Mutual Funds" bucket).
                if (portfolio.getEquityModels() != null) {
                    for (EquityModel model : portfolio.getEquityModels()) {
                        String symbol = model.getSymbol();
                        if (symbol == null || symbol.trim().isEmpty()) {
                            continue;
                        }
                        boolean hasMd = AnalyticsUtils.resolveMarketData(displayMarketData, symbol) != null;
                        String priceSourceHint = hasMd
                                ? HoldingInstrumentClassifier.PRICE_LIVE
                                : HoldingInstrumentClassifier.PRICE_COST;
                        String equitySector;
                        if (mfScope) {
                            // Filtered MF book: parent tiles are SEBI categories only.
                            equitySector = MutualFundSchemeCategoryResolver.resolveDisplayName(
                                    model.getSchemeClass(),
                                    firstNonBlank(model.getName(), model.getCompanyName(), symbol));
                        } else {
                            equitySector = HoldingInstrumentClassifier.resolveSector(
                                    usableEquitySector(model.getSector()),
                                    symbol,
                                    model.getIsin(),
                                    priceSourceHint,
                                    model.getAssetClass(),
                                    model.getName());
                        }
                        if (equitySector == null || HoldingInstrumentClassifier.isMissingSector(equitySector)) {
                            continue;
                        }

                        String currentSector = null;
                        for (Map.Entry<String, List<String>> entry : sectorToStocks.entrySet()) {
                            if (entry.getValue().contains(symbol)) {
                                currentSector = entry.getKey();
                                break;
                            }
                        }
                        boolean forceMfCategory =
                                mfScope
                                        && (currentSector == null
                                                || HoldingInstrumentClassifier.isMissingSector(currentSector)
                                                || MutualFundSchemeCategory.isMutualFundsBucket(currentSector)
                                                || !equitySector.equals(currentSector));
                        // MD often parks NIFTYBEES under Financial Services — force ETF/MF product sectors.
                        boolean forceProductSector =
                                !mfScope
                                        && (HoldingInstrumentClassifier.SECTOR_ETF.equals(equitySector)
                                                || HoldingInstrumentClassifier.SECTOR_MUTUAL_FUND.equals(equitySector)
                                                || HoldingInstrumentClassifier.SECTOR_UNLISTED.equals(equitySector))
                                        && (currentSector == null
                                                || HoldingInstrumentClassifier.isMissingSector(currentSector)
                                                || !equitySector.equals(currentSector));
                        if (forceMfCategory
                                || forceProductSector
                                || currentSector == null
                                || HoldingInstrumentClassifier.isMissingSector(currentSector)) {
                            if (currentSector != null) {
                                sectorToStocks.get(currentSector).remove(symbol);
                                if (sectorToStocks.get(currentSector).isEmpty()) {
                                    sectorToStocks.remove(currentSector);
                                }
                            }
                            sectorToStocks.computeIfAbsent(equitySector, k -> new ArrayList<>()).add(symbol);
                        }
                    }
                }
                
                // Process market data by sector and compute total portfolio value in one pass
                Map<String, List<MarketData>> sectorMarketDataMap = new HashMap<>();
                Map<String, List<Double>> sectorQuantitiesMap = new HashMap<>();
                Map<String, String> symbolPriceSource = new HashMap<>();
                double[] totalPortfolioValue = {0.0};
                groupMarketDataBySector(
                        displayMarketData,
                        sectorToStocks,
                        symbolToQuantity,
                        equityBySymbol,
                        mfMarks,
                        sectorMarketDataMap,
                        sectorQuantitiesMap,
                        symbolPriceSource,
                        totalPortfolioValue);
                
                // Day/% live override only for 1D / live requests. Historical TF uses
                // HeatmapUtils period return from hist previousClose → lastPrice.
                Map<String, Double> symbolToChangePercent = new HashMap<>();
                if (shouldApplyTodayChangeOverride(request) && portfolio.getEquityModels() != null) {
                    for (EquityModel model : portfolio.getEquityModels()) {
                        if (model.getSymbol() != null && model.getTodayProfitLossPercentage() != null
                                && !HoldingInstrumentClassifier.quarantineEquityLtp(
                                        model.getAssetClass(), model.getSymbol(), model.getIsin(), model.getName())) {
                            symbolToChangePercent.put(model.getSymbol(), model.getTodayProfitLossPercentage());
                        }
                    }
                }
                
                // Calculate performance for each sector
                List<Heatmap.SectorPerformance> sectorPerformances = calculateSectorPerformances(
                        sectorMarketDataMap,
                        sectorQuantitiesMap,
                        symbolToQuantity,
                        symbolToChangePercent,
                        symbolPriceSource,
                        equityBySymbol,
                        totalPortfolioValue[0]);
                
                // Create heatmap with domain-driven approach
                Heatmap heatmap = Heatmap.builder()
                    .portfolioId(portfolioId)
                    .timestamp(Instant.now())
                    .sectors(sectorPerformances)
                    .build();
                    
                // Use domain method to sort sectors
                heatmap.sortSectorsByPerformance();
                
                log.info("Generated heatmap with {} sectors for portfolio: {}", sectorPerformances.size(), portfolioId);
                
                if (heatmapRedisService != null
                        && !skipHeatmapCacheRead
                        && heatmap.getSectors() != null
                        && !heatmap.getSectors().isEmpty()) {
                    heatmapRedisService.cacheHeatmap(heatmap, portfolioId, request);
                }
                
                return heatmap;
            }
        );
    }
    
    /**
     * Create empty result when no data is available
     */
    private Heatmap createEmptyResult() {
        return Heatmap.builder()
            .timestamp(Instant.now())
            .sectors(Collections.emptyList())
            .build();
    }

    /**
     * Holdings day P&amp;L override applies only for live / 1D heatmap.
     * Non-1D requests must use hist period returns from market data.
     */
    @Override
    protected boolean allowsEmptyMarketData() {
        // Mutual-fund mosaic builds from AMFI NAV synthetics when OHLC is empty.
        return true;
    }

    @Override
    protected boolean allowsPrefetchFanOut() {
        // Empty/skipped prefetch must not trigger per-symbol OHLC fan-out (30–90s).
        // MF mosaic uses NAV synthetics via allowsEmptyMarketData().
        return false;
    }

    @Override
    protected boolean allowsSecurityDetailsFanOutWhenPrefetchEmpty() {
        // Named sectors for Equity mosaic (do not paint every listing as Unknown).
        return true;
    }

    static boolean shouldApplyTodayChangeOverride(AdvancedAnalyticsRequest request) {
        if (request == null || request.getTimeFrameRequest() == null) {
            return true;
        }
        com.portfolio.model.market.TimeFrame tf = request.getTimeFrame();
        return tf == null || tf == com.portfolio.model.market.TimeFrame.DAY;
    }

    /** Normalize {@code ALL|EQUITY|ETF|MUTUAL_FUND}; blank → ALL. */
    static String normalizeAssetScope(String assetScope) {
        if (assetScope == null || assetScope.isBlank()) {
            return "ALL";
        }
        String u = assetScope.trim().toUpperCase(Locale.ROOT);
        if ("EQUITY".equals(u) || "ETF".equals(u) || "ETFS".equals(u)
                || "MUTUAL_FUND".equals(u) || "MF".equals(u)) {
            if ("MF".equals(u)) {
                return "MUTUAL_FUND";
            }
            if ("ETFS".equals(u)) {
                return "ETF";
            }
            return u;
        }
        return "ALL";
    }

    static boolean matchesAssetScope(EquityModel equity, String assetScope) {
        if (equity == null) {
            return false;
        }
        String scope = normalizeAssetScope(assetScope);
        if ("ALL".equals(scope)) {
            return true;
        }
        String product = HoldingInstrumentClassifier.productClass(
                equity.getAssetClass(),
                equity.getSymbol(),
                equity.getIsin(),
                null,
                equity.getName());
        boolean mutualFund = HoldingInstrumentClassifier.PRODUCT_MUTUAL_FUND.equals(product);
        boolean etf = HoldingInstrumentClassifier.PRODUCT_ETF.equals(product);
        if ("MUTUAL_FUND".equals(scope)) {
            return mutualFund;
        }
        if ("ETF".equals(scope)) {
            return etf;
        }
        // EQUITY: listed equity + unlisted; never scheme MF or listed ETF.
        return !mutualFund && !etf;
    }

    static PortfolioModelV1 filterPortfolioByAssetScope(PortfolioModelV1 portfolio, String assetScope) {
        if (portfolio == null || portfolio.getEquityModels() == null) {
            return portfolio;
        }
        if ("ALL".equals(normalizeAssetScope(assetScope))) {
            return portfolio;
        }
        List<EquityModel> filtered = portfolio.getEquityModels().stream()
                .filter(e -> matchesAssetScope(e, assetScope))
                .collect(Collectors.toList());
        return PortfolioModelV1.builder()
                .id(portfolio.getId())
                .name(portfolio.getName())
                .owner(portfolio.getOwner())
                .portfolioKind(portfolio.getPortfolioKind())
                .brokerType(portfolio.getBrokerType())
                .totalValue(portfolio.getTotalValue())
                .equityModels(filtered)
                .mutualFunds(portfolio.getMutualFunds())
                .bonds(portfolio.getBonds())
                .commodities(portfolio.getCommodities())
                .cash(portfolio.getCash())
                .npsModels(portfolio.getNpsModels())
                .build();
    }

    private static void applyAssetScopeToPrefetch(AdvancedAnalyticsRequest request) {
        if (request == null || request.getPrefetchedPortfolio() == null) {
            return;
        }
        request.setPrefetchedPortfolio(
                filterPortfolioByAssetScope(request.getPrefetchedPortfolio(), request.getAssetScope()));
    }
    
    /**
     * Create a map of symbol to holding quantity.
     * Duplicate symbols still sum — callers must not invent colliding display tickers for distinct ISINs.
     */
    private Map<String, Double> createSymbolToQuantityMap(PortfolioModelV1 portfolio) {
        log.debug("Creating symbol to quantity map for portfolio: {}", portfolio.getName());
        return portfolio.getEquityModels().stream()
            .filter(e -> e != null && e.getSymbol() != null && !e.getSymbol().isBlank())
            .collect(Collectors.toMap(
                EquityModel::getSymbol,
                e -> e.getQuantity() != null ? e.getQuantity() : 0.0,
                (a, b) -> a + b));
    }
    
    /**
     * Group market data by sector. Cost-basis synthetic quotes keep unlisted rows on the mosaic.
     */
    private void groupMarketDataBySector(
            Map<String, MarketData> marketData,
            Map<String, List<String>> sectorToStocks,
            Map<String, Double> symbolToQuantity,
            Map<String, EquityModel> equityBySymbol,
            Map<String, MutualFundNavMark> mfMarks,
            Map<String, List<MarketData>> sectorMarketDataMap,
            Map<String, List<Double>> sectorQuantitiesMap,
            Map<String, String> symbolPriceSource,
            double[] totalPortfolioValue) {
        log.debug("Grouping {} stocks by sector", marketData != null ? marketData.size() : 0);
        
        // Build inverted index for O(1) sector lookup
        Map<String, String> symbolToSector = new HashMap<>();
        sectorToStocks.forEach((sector, symbols) -> 
            symbols.forEach(sym -> symbolToSector.put(sym, sector))
        );
        
        for (String symbol : symbolToQuantity.keySet()) {
            EquityModel equity = equityBySymbol.get(symbol);
            MarketData data = AnalyticsUtils.resolveMarketData(marketData, symbol);
            String priceSource = HoldingInstrumentClassifier.PRICE_LIVE;
            String mfIsin = equity != null && equity.getIsin() != null && !equity.getIsin().isBlank()
                    ? equity.getIsin() : symbol;
            MutualFundNavMark navMark = mfMarks != null ? mfMarks.get(mfIsin.toUpperCase(Locale.ROOT)) : null;

            // Scheme MF / unsafe AMC→equity: never paint with equity cash LTP.
            HoldingMarkPriceResolver.Mark quarantineMark = equity == null ? null
                    : HoldingMarkPriceResolver.tryQuarantineMark(
                            equity.getAssetClass(),
                            symbol,
                            equity.getIsin(),
                            equity.getName(),
                            equity.getImportMarkPrice(),
                            equity.getCurrentValue(),
                            equity.getQuantity(),
                            equity.getCurrentPrice(),
                            equity.getAvgBuyingPrice(),
                            equity.getInvestmentValue());
            if (navMark != null && navMark.nav() > 0) {
                // 1D % only when prior published NAV exists; never invent previousClose=last.
                var navBuilder = MarketData.builder()
                        .symbol(symbol)
                        .lastPrice(navMark.nav())
                        .timestamp(navMark.navDate() == null ? null : navMark.navDate()
                                .atStartOfDay(java.time.ZoneId.of("Asia/Kolkata")).toInstant());
                if (navMark.previousNav() != null && navMark.previousNav() > 0) {
                    navBuilder.previousClose(navMark.previousNav());
                }
                data = navBuilder.build();
                priceSource = HoldingInstrumentClassifier.PRICE_NAV;
                if (equity != null && navMark.schemeName() != null && !navMark.schemeName().isBlank()) {
                    equity.setName(navMark.schemeName());
                }
                log.info("Heatmap EOD NAV for symbol={} nav={} prev={} date={}",
                        symbol, navMark.nav(), navMark.previousNav(), navMark.navDate());
            } else if (quarantineMark != null && quarantineMark.isPresent()) {
                // Size only — omit previousClose so heatmap does not paint fake 0% day change.
                data = MarketData.builder()
                        .symbol(symbol)
                        .lastPrice(quarantineMark.price())
                        .build();
                priceSource = quarantineMark.priceSource();
                log.info("Heatmap quarantine mark for symbol={} price={} source={}",
                        symbol, quarantineMark.price(), priceSource);
            } else if (quarantineMark != null) {
                Double cost = costBasisPrice(equity);
                if (cost == null || cost <= 0) {
                    log.warn("Quarantined symbol={} has no NAV or safe fallback", symbol);
                    continue;
                }
                data = MarketData.builder().symbol(symbol).lastPrice(cost).build();
                priceSource = HoldingInstrumentClassifier.PRICE_COST;
            } else if (data == null || !hasResolvablePrice(data)) {
                Double cost = costBasisPrice(equity);
                if (cost == null || cost <= 0) {
                    log.warn("Null market data and no cost basis for symbol: {}", symbol);
                    continue;
                }
                data = MarketData.builder()
                        .symbol(symbol)
                        .lastPrice(cost)
                        .build();
                priceSource = HoldingInstrumentClassifier.PRICE_COST;
                log.info("Heatmap cost-basis fallback for symbol={} price={}", symbol, cost);
            }

            // Trust sectorToStocks (incl. SEBI MF categories). Force ETF/MF product sectors;
            // fill Unknown/blank via classifier. Never leave NIFTYBEES under Financial Services.
            String sector = symbolToSector.getOrDefault(symbol, "Unknown");
            String classified = HoldingInstrumentClassifier.resolveSector(
                    HoldingInstrumentClassifier.isMissingSector(sector) ? null : sector,
                    symbol,
                    equity != null ? equity.getIsin() : null,
                    priceSource,
                    equity != null ? equity.getAssetClass() : null,
                    equity != null ? firstNonBlank(equity.getName(), equity.getCompanyName()) : null);
            if (HoldingInstrumentClassifier.SECTOR_ETF.equals(classified)
                    || HoldingInstrumentClassifier.SECTOR_MUTUAL_FUND.equals(classified)
                    || HoldingInstrumentClassifier.SECTOR_UNLISTED.equals(classified)) {
                if (!MutualFundSchemeCategory.isKnownDisplayName(sector)) {
                    sector = classified;
                }
            } else if (HoldingInstrumentClassifier.isMissingSector(sector)
                    || MutualFundSchemeCategory.isMutualFundsBucket(sector)) {
                if (classified != null
                        && !HoldingInstrumentClassifier.isMissingSector(classified)
                        && !MutualFundSchemeCategory.isKnownDisplayName(sector)) {
                    sector = classified;
                }
            }
            
            sectorMarketDataMap.computeIfAbsent(sector, k -> new ArrayList<>())
                .add(data);
            
            double quantity = symbolToQuantity.getOrDefault(symbol, 0.0);
            sectorQuantitiesMap.computeIfAbsent(sector, k -> new ArrayList<>())
                .add(quantity);
            symbolPriceSource.put(symbol, priceSource);
                
            double resolvedPrice = 0.0;
            if (data.getLastPrice() != null && data.getLastPrice() > 0) {
                resolvedPrice = data.getLastPrice();
            } else if (data.getOhlc() != null && data.getOhlc().getClose() > 0) {
                resolvedPrice = data.getOhlc().getClose();
            } else if (data.getPreviousClose() != null && data.getPreviousClose() > 0) {
                resolvedPrice = data.getPreviousClose();
            }
            totalPortfolioValue[0] += resolvedPrice * quantity;
        }
    }

    private static Map<String, EquityModel> indexEquities(PortfolioModelV1 portfolio) {
        Map<String, EquityModel> map = new HashMap<>();
        if (portfolio == null || portfolio.getEquityModels() == null) {
            return map;
        }
        for (EquityModel e : portfolio.getEquityModels()) {
            if (e != null && e.getSymbol() != null && !e.getSymbol().isBlank()) {
                map.put(e.getSymbol(), e);
            }
        }
        return map;
    }

    /**
     * Force ISIN as the in-memory symbol for scheme MF / unsafe AMC→equity maps so distinct
     * demat schemes that share a poisoned ticker (RECLTD) never collapse into one mosaic cell.
     */
    static void applyIsinBookKeys(PortfolioModelV1 portfolio) {
        if (portfolio == null || portfolio.getEquityModels() == null) {
            return;
        }
        for (EquityModel equity : portfolio.getEquityModels()) {
            if (equity == null) {
                continue;
            }
            String isin = equity.getIsin();
            if (isin == null || isin.isBlank()) {
                continue;
            }
            String isinKey = isin.trim().toUpperCase(Locale.ROOT);
            if (!HoldingInstrumentClassifier.mustKeepIsinBookKey(
                    equity.getAssetClass(), equity.getSymbol(), isinKey, equity.getName())) {
                continue;
            }
            String before = equity.getSymbol();
            if (before == null || !isinKey.equalsIgnoreCase(before.trim())) {
                log.info("Heatmap ISIN book key {} → {}", before, isinKey);
                equity.setSymbol(isinKey);
            }
        }
    }

    /**
     * Replace ISIN-shaped equity symbols with broker display tickers (request-scoped; not persisted).
     * Scheme MF rows that must keep an ISIN book key are left alone — renaming
     * {@code …-DIRECT-GROWTH} tails to {@code GROWTH} collapses many schemes into one mosaic cell.
     */
    static void applyDisplayTickers(PortfolioModelV1 portfolio) {
        if (portfolio == null || portfolio.getEquityModels() == null) {
            return;
        }
        for (EquityModel equity : portfolio.getEquityModels()) {
            if (equity == null) {
                continue;
            }
            String symbol = equity.getSymbol();
            if (!TradingSymbolResolver.looksLikeIsin(symbol)) {
                continue;
            }
            if (equity.getIsin() == null || equity.getIsin().isBlank()) {
                equity.setIsin(symbol.trim().toUpperCase(Locale.ROOT));
            }
            // Never rename scheme-MF ISIN keys to plan-suffix display tokens (GROWTH, etc.).
            if (HoldingInstrumentClassifier.mustKeepIsinBookKey(
                    equity.getAssetClass(), symbol, equity.getIsin(), equity.getName())) {
                continue;
            }
            String suggested = HoldingInstrumentClassifier.suggestDisplayTickerFromName(equity.getName());
            if (suggested == null || suggested.isBlank()) {
                continue;
            }
            if (HoldingInstrumentClassifier.isUnsafeNameToTickerMap(equity.getName(), suggested)) {
                continue;
            }
            log.info("Heatmap display ticker {} → {} (isin={})", symbol, suggested, equity.getIsin());
            equity.setSymbol(suggested);
        }
    }

    /**
     * Re-key market data from ISIN keys to display tickers after {@link #applyDisplayTickers}.
     */
    static Map<String, MarketData> remappedMarketData(
            Map<String, MarketData> marketData, PortfolioModelV1 portfolio) {
        if (marketData == null || marketData.isEmpty() || portfolio == null
                || portfolio.getEquityModels() == null) {
            return marketData != null ? marketData : Map.of();
        }
        Map<String, MarketData> out = new HashMap<>(marketData);
        for (EquityModel equity : portfolio.getEquityModels()) {
            if (equity == null || equity.getSymbol() == null) {
                continue;
            }
            String display = equity.getSymbol();
            if (out.containsKey(display)) {
                continue;
            }
            String isin = equity.getIsin();
            MarketData byIsin = isin != null ? AnalyticsUtils.resolveMarketData(marketData, isin) : null;
            if (byIsin != null) {
                out.put(display, MarketData.builder()
                        .symbol(display)
                        .lastPrice(byIsin.getLastPrice())
                        .previousClose(byIsin.getPreviousClose())
                        .ohlc(byIsin.getOhlc())
                        .build());
            }
        }
        return out;
    }

    private static String firstNonBlank(String... values) {
        if (values == null) {
            return null;
        }
        for (String v : values) {
            if (v != null && !v.isBlank()) {
                return v.trim();
            }
        }
        return null;
    }

    private static String usableEquitySector(String sector) {
        if (HoldingInstrumentClassifier.isMissingSector(sector)) {
            return null;
        }
        return sector.trim();
    }

    private static boolean hasResolvablePrice(MarketData data) {
        if (data == null) {
            return false;
        }
        if (data.getLastPrice() != null && data.getLastPrice() > 0) {
            return true;
        }
        if (data.getOhlc() != null && data.getOhlc().getClose() > 0) {
            return true;
        }
        return data.getPreviousClose() != null && data.getPreviousClose() > 0;
    }

    /**
     * Book price when live MD is missing: cost, then import/statement mark, then currentPrice.
     */
    static Double costBasisPrice(EquityModel equity) {
        if (equity == null) {
            return null;
        }
        if (equity.getAvgBuyingPrice() != null && equity.getAvgBuyingPrice() > 0) {
            return equity.getAvgBuyingPrice();
        }
        if (equity.getInvestmentValue() != null
                && equity.getQuantity() != null
                && equity.getQuantity() > 0
                && equity.getInvestmentValue() > 0) {
            return equity.getInvestmentValue() / equity.getQuantity();
        }
        if (equity.getImportMarkPrice() != null && equity.getImportMarkPrice() > 0) {
            return equity.getImportMarkPrice();
        }
        if (equity.getCurrentPrice() != null && equity.getCurrentPrice() > 0) {
            return equity.getCurrentPrice();
        }
        return null;
    }
    
    
    
    /**
     * Calculate performance for each sector with domain-driven approach
     */
    private List<Heatmap.SectorPerformance> calculateSectorPerformances(
            Map<String, List<MarketData>> sectorMarketDataMap,
            Map<String, List<Double>> sectorQuantitiesMap,
            Map<String, Double> symbolToQuantity,
            Map<String, Double> symbolToChangePercent,
            Map<String, String> symbolPriceSource,
            Map<String, EquityModel> equityBySymbol,
            double totalPortfolioValue) {
        
        log.debug("Calculating performance metrics for {} sectors", sectorMarketDataMap.size());
        List<Heatmap.SectorPerformance> sectorPerformances = new ArrayList<>();
        
        log.debug("Total portfolio value for weightage calculation: {}", totalPortfolioValue);
        
        for (Map.Entry<String, List<MarketData>> entry : sectorMarketDataMap.entrySet()) {
            String sectorName = entry.getKey();
            List<MarketData> sectorStocks = entry.getValue();
            List<Double> quantities = sectorQuantitiesMap.get(sectorName);
            
            // Extract symbols for this sector
            List<String> symbols = new ArrayList<>();
            for (MarketData data : sectorStocks) {
                // Get symbol from MarketData or use a placeholder if null
                String symbol = data.getSymbol();
                if (symbol == null || symbol.isEmpty()) {
                    // Try to find the symbol by looking up in the symbolToQuantity map
                    for (Map.Entry<String, Double> symbolEntry : symbolToQuantity.entrySet()) {
                        if (symbolEntry.getValue().equals(quantities.get(symbols.size()))) {
                            symbol = symbolEntry.getKey();
                            break;
                        }
                    }
                    
                    // If still null, use a placeholder
                    if (symbol == null || symbol.isEmpty()) {
                        symbol = "UNKNOWN-" + symbols.size();
                    }
                }
                symbols.add(symbol);
                log.debug("Added symbol: {} to sector: {}", symbol, sectorName);
            }
            
            // Create complete sector performance with stock details using domain-driven approach
            Heatmap.SectorPerformance sectorPerformance = HeatmapUtils.createCompleteSectorPerformance(
                sectorName,
                null, // Let the utility generate a sector code
                sectorStocks,
                quantities,
                symbols,
                totalPortfolioValue,
                symbolToChangePercent);

            if (sectorPerformance.getStocks() != null) {
                for (Heatmap.StockDetail stock : sectorPerformance.getStocks()) {
                    if (stock == null || stock.getSymbol() == null) {
                        continue;
                    }
                    if (symbolPriceSource != null) {
                        stock.setPriceSource(symbolPriceSource.get(stock.getSymbol()));
                    }
                    EquityModel equity = equityBySymbol != null ? equityBySymbol.get(stock.getSymbol()) : null;
                    if (equity != null) {
                        if (equity.getName() != null && !equity.getName().isBlank()) {
                            stock.setName(equity.getName());
                        }
                        String schemeIsin = equity.getIsin();
                        if (schemeIsin == null || schemeIsin.isBlank()) {
                            schemeIsin = stock.getSymbol();
                        }
                        if (schemeIsin != null && !schemeIsin.isBlank()) {
                            stock.setIsin(schemeIsin.trim().toUpperCase(Locale.ROOT));
                        }
                    } else if (HoldingInstrumentClassifier.isSchemeMfIsin(stock.getSymbol())) {
                        stock.setIsin(stock.getSymbol().trim().toUpperCase(Locale.ROOT));
                    }
                }
            }
            
            sectorPerformances.add(sectorPerformance);
        }
        
        return sectorPerformances;
    }
    
    // calculateTotalSectorValues has been merged into groupMarketDataBySector for performance
    
    // SectorMetrics class and related methods have been moved to HeatmapUtils
}
