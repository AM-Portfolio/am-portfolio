package com.portfolio.service.scheduler;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.am.common.amcommondata.model.PortfolioModelV1;
import com.am.common.amcommondata.service.PortfolioService;
import com.portfolio.marketdata.service.MarketDataService;
import com.portfolio.model.market.MarketData;
import com.portfolio.redis.service.PortfolioMarketDataRedisService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Component
@Slf4j
@RequiredArgsConstructor
public class MarketDataWarmUpScheduler {

    private final PortfolioService portfolioService;
    private final MarketDataService marketDataService;
    private final PortfolioMarketDataRedisService marketDataRedisService;

    // Fires at :00 and :10 past each hour from 9 AM to 4 PM IST Mon-Fri
    @Scheduled(cron = "0 */10 9-16 * * MON-FRI", zone = "Asia/Kolkata")
    public void warmUpMarketData() {
        log.info("[WarmUp] Starting market data pre-warm job");

        List<String> allUserIds = portfolioService.getAllUserIds();
        if (allUserIds == null || allUserIds.isEmpty()) {
            log.info("[WarmUp] No users found. Skipping.");
            return;
        }

        // Collect all unique symbols across ALL users to avoid N+1 MongoDB calls
        Set<String> allSymbols = new HashSet<>();
        for (String userId : allUserIds) {
            try {
                List<PortfolioModelV1> portfolios = portfolioService.getPortfoliosByUserId(userId);
                if (portfolios != null) {
                    portfolios.forEach(p -> {
                        if (p.getEquityModels() != null) {
                            p.getEquityModels().forEach(e -> {
                                if (e.getSymbol() != null) {
                                    allSymbols.add(e.getSymbol());
                                }
                            });
                        }
                    });
                }
            } catch (Exception e) {
                log.warn("[WarmUp] Failed to collect symbols for user {}: {}", userId, e.getMessage());
            }
        }

        if (allSymbols.isEmpty()) {
            log.info("[WarmUp] No symbols found across all users. Skipping fetch.");
            return;
        }

        // Single batch OHLC call for ALL symbols across all users
        log.info("[WarmUp] Fetching OHLC data for {} unique symbols", allSymbols.size());
        try {
            Map<String, MarketData> data = marketDataService.getOhlcData(new ArrayList<>(allSymbols), false);
            if (data != null && !data.isEmpty()) {
                marketDataRedisService.cacheMarketData(data);
                log.info("[WarmUp] Pre-warmed {} symbols into Redis", data.size());
            } else {
                log.warn("[WarmUp] No market data returned from API");
            }
        } catch (Exception e) {
            log.error("[WarmUp] Failed to fetch and cache market data", e);
        }
    }
}
