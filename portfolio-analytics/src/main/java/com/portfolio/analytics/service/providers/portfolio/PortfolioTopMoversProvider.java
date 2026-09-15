package com.portfolio.analytics.service.providers.portfolio;

import com.am.common.amcommondata.service.PortfolioService;
import com.portfolio.analytics.model.AnalyticsType;
import com.portfolio.analytics.service.utils.SecurityDetailsService;
import com.portfolio.analytics.service.utils.TopMoverUtils;
import com.portfolio.marketdata.service.MarketDataService;
import com.portfolio.model.analytics.GainerLoser;
import com.portfolio.model.analytics.request.AdvancedAnalyticsRequest;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.*;

/**
 * Provider for portfolio top movers (gainers and losers) analytics
 */
@Service
@Slf4j
public class PortfolioTopMoversProvider extends AbstractPortfolioAnalyticsProvider<GainerLoser> {

    public static final int DEFAULT_LIMIT = 5;

    public PortfolioTopMoversProvider(PortfolioService portfolioService, MarketDataService marketDataService, SecurityDetailsService securityDetailsService) {
        super(portfolioService, marketDataService, securityDetailsService);
    }

    @Override
    public AnalyticsType getType() {
        return AnalyticsType.TOP_MOVERS;
    }

    @Override
    public GainerLoser generateAnalytics(AdvancedAnalyticsRequest request) {
        // Default to the default limit from TopMoverUtils
        return generateAnalytics(request.getCoreIdentifiers().getPortfolioId(), request);
    }
    
    @Override
    public GainerLoser generateAnalytics(String portfolioId, AdvancedAnalyticsRequest request) {
        // Extract movers limit from feature configuration
        Integer moversLimit = request.getFeatureConfiguration().getMoversLimit();
        int limit = moversLimit != null ? moversLimit : 
                    (request.getPagination().isReturnAllData() ? DEFAULT_LIMIT : request.getPagination().getSize());
        
        log.info("Generating top {} movers for portfolio {} (always live day change)", 
                limit, portfolioId);
        
        // Always use live market data for Overview day movers — ignore chart timeframe
        // so historical candle open is never mistaken for previousClose.
        return processPortfolioData(
            portfolioId,
            request,
            this::createEmptyResponse,
            (portfolio, portfolioSymbols, marketData) -> {
                Map<String, String> symbolSectors = new HashMap<>();
                if (portfolio.getEquityModels() != null) {
                    for (com.am.common.amcommondata.model.asset.equity.EquityModel model : portfolio.getEquityModels()) {
                        String symbol = model.getSymbol();
                        if (symbol != null && !symbol.trim().isEmpty()) {
                            String sector = (model.getSector() != null && !model.getSector().trim().isEmpty() && !model.getSector().trim().equals("-"))
                                ? model.getSector().trim() : "Unknown";
                            symbolSectors.put(symbol, sector);
                        }
                    }
                }
                
                return TopMoverUtils.buildTopMoversResponse(portfolioSymbols, marketData, limit, portfolioId, true, symbolSectors);
            }
        );
    }

    @Override
    protected Map<String, com.portfolio.model.market.MarketData> resolvePrefetchedMarketData(AdvancedAnalyticsRequest request) {
        if (request != null
                && request.getPrefetchedLiveMarketData() != null
                && !request.getPrefetchedLiveMarketData().isEmpty()) {
            return request.getPrefetchedLiveMarketData();
        }
        return super.resolvePrefetchedMarketData(request);
    }

    
    
    /**
     * Creates an empty response when no data is available
     */
    private GainerLoser createEmptyResponse() {
        return GainerLoser.builder()
            .timestamp(Instant.now())
            .topGainers(Collections.emptyList())
            .topLosers(Collections.emptyList())
            .build();
    }
}
