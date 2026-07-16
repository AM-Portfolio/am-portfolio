package com.portfolio.app.metrics;

import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.am.common.amcommondata.document.portfolio.PortfolioDocument;
import com.am.common.amcommondata.repository.portfolio.PortfolioDocumentRepository;

import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.extern.slf4j.Slf4j;

/**
 * Domain gauges for Functional / Services dashboard:
 * users (distinct owners), portfolios, equity holdings.
 * Refreshed on a schedule so Prometheus scrape stays cheap.
 */
@Component
@Slf4j
public class PortfolioBusinessMetrics {

    private final PortfolioDocumentRepository portfolioDocumentRepository;
    private final AtomicLong users = new AtomicLong();
    private final AtomicLong portfolios = new AtomicLong();
    private final AtomicLong holdings = new AtomicLong();

    public PortfolioBusinessMetrics(
            MeterRegistry registry,
            PortfolioDocumentRepository portfolioDocumentRepository) {
        this.portfolioDocumentRepository = portfolioDocumentRepository;
        Gauge.builder("portfolio.users.total", users, AtomicLong::doubleValue)
                .description("Distinct portfolio owners")
                .register(registry);
        Gauge.builder("portfolio.portfolios.total", portfolios, AtomicLong::doubleValue)
                .description("Portfolio documents")
                .register(registry);
        Gauge.builder("portfolio.holdings.total", holdings, AtomicLong::doubleValue)
                .description("Equity holdings across portfolios")
                .register(registry);
        refresh();
    }

    @Scheduled(fixedDelayString = "${am.observability.business-metrics.refresh-ms:60000}")
    public void refresh() {
        try {
            List<String> owners = portfolioDocumentRepository.findAllDistinctOwners();
            users.set(owners != null ? owners.size() : 0);

            List<PortfolioDocument> docs = portfolioDocumentRepository.findAll();
            portfolios.set(docs != null ? docs.size() : 0);

            long equityCount = 0;
            if (docs != null) {
                for (PortfolioDocument doc : docs) {
                    if (doc.getEquities() != null) {
                        equityCount += doc.getEquities().size();
                    }
                }
            }
            holdings.set(equityCount);
        } catch (Exception e) {
            log.warn("Failed to refresh portfolio business metrics: {}", e.toString());
        }
    }
}
