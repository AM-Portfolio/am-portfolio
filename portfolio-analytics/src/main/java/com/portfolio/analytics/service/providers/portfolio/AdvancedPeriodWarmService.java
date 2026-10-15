package com.portfolio.analytics.service.providers.portfolio;

import com.am.common.amcommondata.model.PortfolioModelV1;
import com.am.common.amcommondata.model.asset.equity.EquityModel;
import com.portfolio.marketdata.service.MarketDataService;
import com.portfolio.marketdata.util.HistoricalDataRequestFactory;
import com.portfolio.model.analytics.Heatmap;
import com.portfolio.model.analytics.request.AdvancedAnalyticsRequest;
import com.portfolio.model.analytics.request.CoreIdentifiers;
import com.portfolio.model.analytics.request.FeatureToggles;
import com.portfolio.model.analytics.request.TimeFrameRequest;
import com.portfolio.model.market.MarketData;
import com.portfolio.model.market.TimeFrame;
import com.portfolio.redis.service.PortfolioHeatmapRedisService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import java.util.stream.Collectors;

/**
 * Off-request-path warm of START_END hist → period heatmap Redis for advanced analytics.
 * HTTP advanced must never await this.
 */
@Service
@Slf4j
public class AdvancedPeriodWarmService {

    private final MarketDataService marketDataService;
    private final PortfolioAnalyticsFacade portfolioAnalyticsFacade;
    private final Executor warmExecutor;
    private final PortfolioHeatmapRedisService heatmapRedisService;
    private final ConcurrentHashMap<String, Boolean> inFlight = new ConcurrentHashMap<>();

    public AdvancedPeriodWarmService(
            MarketDataService marketDataService,
            @Autowired @org.springframework.context.annotation.Lazy PortfolioAnalyticsFacade portfolioAnalyticsFacade,
            @Qualifier("advancedWarmExecutor") Executor warmExecutor,
            @Nullable PortfolioHeatmapRedisService heatmapRedisService) {
        this.marketDataService = marketDataService;
        this.portfolioAnalyticsFacade = portfolioAnalyticsFacade;
        this.warmExecutor = warmExecutor;
        this.heatmapRedisService = heatmapRedisService;
    }

    public void warmAsync(AdvancedAnalyticsRequest source) {
        if (source == null || source.getPrefetchedPortfolio() == null) {
            return;
        }
        String portfolioId = source.getCoreIdentifiers() != null
                ? source.getCoreIdentifiers().getPortfolioId()
                : null;
        if (portfolioId == null || portfolioId.isBlank()) {
            return;
        }

        TimeFrame tf = source.getTimeFrame() != null ? source.getTimeFrame() : TimeFrame.DAY;
        warmExecutor.execute(() -> warmOne(source.getPrefetchedPortfolio(), portfolioId, tf, true));
    }

    private void warmOne(PortfolioModelV1 portfolio, String portfolioId, TimeFrame tf, boolean prewarmOthers) {
        String lockKey = portfolioId + "|" + (tf != null ? tf.getValue() : "live");
        if (inFlight.putIfAbsent(lockKey, Boolean.TRUE) != null) {
            log.debug("[AdvancedWarm] skip in-flight {}", lockKey);
            return;
        }
        try {
            List<String> symbols = portfolio.getEquityModels() == null
                    ? List.of()
                    : portfolio.getEquityModels().stream()
                            .map(EquityModel::getSymbol)
                            .filter(Objects::nonNull)
                            .filter(s -> !s.isEmpty())
                            .distinct()
                            .collect(Collectors.toList());
            if (symbols.isEmpty()) {
                return;
            }

            TimeFrameRequest tfr = TimeFrameRequest.builder().timeFrame(tf).build();
            if (HistoricalDataRequestFactory.isSameDayLiveWindow(tfr)) {
                log.info("[AdvancedWarm] same-day TF — skip hist warm portfolioId={}", portfolioId);
                return;
            }

            var histReq = HistoricalDataRequestFactory.forPeriodEndpoints(symbols, tfr);
            log.info("[AdvancedWarm] START_END hist symbols={} tf={} portfolioId={}",
                    symbols.size(), tf, portfolioId);
            Map<String, MarketData> hist = marketDataService.getHistoricalData(histReq);
            if (hist == null || hist.isEmpty()) {
                log.warn("[AdvancedWarm] empty hist — not writing Redis portfolioId={} tf={}", portfolioId, tf);
                return;
            }

            AdvancedAnalyticsRequest warmReq = new AdvancedAnalyticsRequest();
            warmReq.setCoreIdentifiers(CoreIdentifiers.builder().portfolioId(portfolioId).build());
            warmReq.setTimeFrame(tf);
            warmReq.setFromDate(histReq.getFromDate() != null
                    ? java.time.LocalDate.parse(histReq.getFromDate()) : null);
            warmReq.setToDate(histReq.getToDate() != null
                    ? java.time.LocalDate.parse(histReq.getToDate()) : null);
            FeatureToggles toggles = new FeatureToggles();
            toggles.setIncludeHeatmap(true);
            toggles.setIncludeMovers(false);
            toggles.setIncludeSectorAllocation(false);
            toggles.setIncludeMarketCapAllocation(false);
            warmReq.setFeatureToggles(toggles);
            warmReq.setPrefetchedPortfolio(portfolio);
            warmReq.setPrefetchAttempted(true);
            warmReq.setPrefetchedMarketData(hist);
            warmReq.setAllowHeatmapRedisWrite(true);

            Heatmap heatmap = portfolioAnalyticsFacade.generateSectorHeatmap(warmReq);
            if (heatmap == null || heatmap.getSectors() == null || heatmap.getSectors().isEmpty()) {
                log.warn("[AdvancedWarm] empty heatmap after hist — skip Redis write portfolioId={}", portfolioId);
                return;
            }
            if (heatmapRedisService != null) {
                heatmapRedisService.cacheHeatmap(heatmap, portfolioId, warmReq);
            }
            portfolioAnalyticsFacade.invalidateL1(portfolioId);
            log.info("[AdvancedWarm] PERIOD heatmap cached portfolioId={} tf={} sectors={}",
                    portfolioId, tf, heatmap.getSectors().size());

            if (prewarmOthers) {
                for (TimeFrame next : List.of(TimeFrame.WEEK, TimeFrame.MONTH, TimeFrame.YEAR)) {
                    if (tf != null && tf.equals(next)) {
                        continue;
                    }
                    warmOne(portfolio, portfolioId, next, false);
                }
            }
        } catch (Exception e) {
            log.warn("[AdvancedWarm] failed portfolioId={} tf={}: {}", portfolioId, tf, e.getMessage());
        } finally {
            inFlight.remove(lockKey);
        }
    }
}
