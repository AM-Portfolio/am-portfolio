package com.portfolio.service.scheduler;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.am.common.amcommondata.service.PortfolioService;
import com.portfolio.model.TimeInterval;
import com.portfolio.service.portfolio.PortfolioHoldingsService;

import com.am.common.amcommondata.document.portfolio.HoldingSnapshotItem;
import com.am.common.amcommondata.document.portfolio.PortfolioSnapshotEntry;
import com.am.common.amcommondata.model.PortfolioModelV1;
import com.am.common.amcommondata.service.PortfolioSnapshotService;
import com.portfolio.model.portfolio.EquityHoldings;
import com.portfolio.model.portfolio.PortfolioHoldings;
import java.util.ArrayList;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Component
@Slf4j
@RequiredArgsConstructor
public class PortfolioHistoryScheduler {

    private final PortfolioService portfolioService;
    private final PortfolioHoldingsService portfolioHoldingsService;
    private final PortfolioSnapshotService portfolioSnapshotService;

    // Runs every day at 17:00 (5 PM) IST (Asia/Kolkata)
    // Cron: Second, Minute, Hour, Day of Month, Month, Day of Week
    @Scheduled(cron = "0 0 17 * * *", zone = "Asia/Kolkata")
    public void runEndOfDayJob() {
        log.info("Starting End-of-Day Portfolio History Job at {}", LocalDateTime.now());

        try {
            List<String> userIds = portfolioService.getAllUserIds();
            log.info("Found {} users for history generation", userIds.size());

            int processed = 0;
            int errors = 0;

            for (String userId : userIds) {
                try {
                    List<PortfolioModelV1> portfolios = portfolioService.getPortfoliosByUserId(userId);
                    List<PortfolioSnapshotEntry> entries = new ArrayList<>();
                    double totalWealth = 0.0;
                    double totalInvestment = 0.0;

                    for (PortfolioModelV1 portfolio : portfolios) {
                        String portfolioId = portfolio.getId().toString();
                        PortfolioHoldings enrichedHoldings = portfolioHoldingsService.getPortfolioHoldings(userId, portfolioId, TimeInterval.ONE_DAY, true);

                        if (enrichedHoldings == null || enrichedHoldings.getEquityHoldings() == null) continue;

                        double portValue = 0.0;
                        double portInvestment = 0.0;
                        List<HoldingSnapshotItem> snapshotHoldings = new ArrayList<>();

                        for (EquityHoldings h : enrichedHoldings.getEquityHoldings()) {
                            double price = h.getCurrentPrice() != null ? h.getCurrentPrice() : h.getAverageBuyingPrice();
                            double value = h.getQuantity() * price;
                            double cost = h.getQuantity() * h.getAverageBuyingPrice();
                            
                            portValue += value;
                            portInvestment += cost;
                            
                            snapshotHoldings.add(HoldingSnapshotItem.builder()
                                .symbol(h.getSymbol())
                                .isin(h.getIsin())
                                .quantity(h.getQuantity())
                                .avgBuyPrice(h.getAverageBuyingPrice())
                                .build());
                        }
                        
                        double portGainLoss = portValue - portInvestment;
                        double portGainLossPct = portInvestment > 0 ? (portGainLoss / portInvestment) * 100.0 : 0.0;

                        entries.add(PortfolioSnapshotEntry.builder()
                                .portfolioId(portfolioId)
                                .portfolioName(portfolio.getName())
                                .brokerType(portfolio.getBrokerType() != null ? portfolio.getBrokerType().name() : null)
                                .open(portValue)
                                .high(portValue)
                                .low(portValue)
                                .close(portValue)
                                .totalInvestment(portInvestment)
                                .totalGainLoss(portGainLoss)
                                .totalGainLossPercentage(portGainLossPct)
                                .holdings(snapshotHoldings)
                                .build());
                                
                        totalWealth += portValue;
                        totalInvestment += portInvestment;
                    }

                    if (!entries.isEmpty()) {
                        double totalGainLoss = totalWealth - totalInvestment;
                        double totalGainLossPct = totalInvestment > 0 ? (totalGainLoss / totalInvestment) * 100.0 : 0.0;

                        portfolioSnapshotService.saveUserSnapshot(userId, totalWealth, totalInvestment, totalGainLoss, totalGainLossPct, entries);
                        processed++;
                    }
                } catch (Exception e) {
                    log.error("Failed to generate history for user: {}", userId, e);
                    errors++;
                }
            }

            log.info("Completed End-of-Day Job. Processed: {}, Errors: {}", processed, errors);

        } catch (Exception e) {
            log.error("Critical failure in End-of-Day Job", e);
        }
    }
}
