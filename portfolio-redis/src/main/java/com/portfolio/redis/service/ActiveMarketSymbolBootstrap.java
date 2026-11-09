package com.portfolio.redis.service;

import com.am.common.amcommondata.document.portfolio.PortfolioDocument;
import com.am.common.amcommondata.repository.portfolio.PortfolioDocumentRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * One-shot bootstrap so existing Mongo holdings enter {@code market:active-symbols}
 * without requiring every user to re-upload.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ActiveMarketSymbolBootstrap {

    private final PortfolioDocumentRepository portfolioDocumentRepository;
    private final ActiveMarketSymbolPublisher activeMarketSymbolPublisher;

    @Value("${market.active-symbols.bootstrap-on-startup:true}")
    private boolean bootstrapOnStartup;

    @EventListener(ApplicationReadyEvent.class)
    public void onReady() {
        if (!bootstrapOnStartup) {
            log.info("ActiveMarketSymbolBootstrap: skipped (market.active-symbols.bootstrap-on-startup=false)");
            return;
        }
        new Thread(this::bootstrapAll, "active-market-symbols-bootstrap").start();
    }

    public void bootstrapAll() {
        try {
            List<PortfolioDocument> all = portfolioDocumentRepository.findAll();
            if (all == null || all.isEmpty()) {
                log.info("ActiveMarketSymbolBootstrap: no portfolios found");
                return;
            }
            log.info("ActiveMarketSymbolBootstrap: publishing symbols from {} portfolios", all.size());
            activeMarketSymbolPublisher.publishFromDocuments(all);
        } catch (Exception e) {
            log.warn("ActiveMarketSymbolBootstrap: failed (fail-open): {}", e.getMessage());
        }
    }
}
