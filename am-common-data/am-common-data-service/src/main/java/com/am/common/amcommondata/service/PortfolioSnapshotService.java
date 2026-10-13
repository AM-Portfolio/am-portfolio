package com.am.common.amcommondata.service;

import com.am.common.amcommondata.document.portfolio.PortfolioSnapshotDocument;
import com.am.common.amcommondata.document.portfolio.PortfolioSnapshotEntry;
import com.am.common.amcommondata.model.PortfolioSnapshotEntryModel;
import com.am.common.amcommondata.model.PortfolioSnapshotModel;
import com.am.common.amcommondata.repository.portfolio.PortfolioSnapshotRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class PortfolioSnapshotService {

    private final PortfolioSnapshotRepository portfolioSnapshotRepository;

    private static final ZoneId IST = ZoneId.of("Asia/Kolkata");

    /**
     * 1D lookback must span weekends/holidays. Config:
     * {@code portfolio.snapshot.one-day-lookback-days} (default 10).
     * Callers still pick the latest prior-session baseline from the window.
     */
    @org.springframework.beans.factory.annotation.Value("${portfolio.snapshot.one-day-lookback-days:10}")
    private int oneDayLookbackDays;

    private static final java.util.Map<String, java.time.Period> TIMEFRAME_PERIODS = java.util.Map.of(
        "1W",  java.time.Period.ofWeeks(1),
        "1M",  java.time.Period.ofMonths(1),
        "3M",  java.time.Period.ofMonths(3),
        "6M",  java.time.Period.ofMonths(6),
        "1Y",  java.time.Period.ofYears(1),
        "3Y",  java.time.Period.ofYears(3),
        "5Y",  java.time.Period.ofYears(5)
    );

    @org.springframework.cache.annotation.CacheEvict(value = "portfolioHistory", allEntries = true)
    public void saveUserSnapshot(String userId, Double totalUserWealth, Double totalUserInvestment, Double totalUserGainLoss, Double totalUserGainLossPercentage, List<PortfolioSnapshotEntry> entries) {
        saveUserSnapshot(userId, totalUserWealth, totalUserInvestment, totalUserGainLoss, totalUserGainLossPercentage, entries, LocalDate.now());
    }

    @org.springframework.cache.annotation.CacheEvict(value = "portfolioHistory", allEntries = true)
    public void saveUserSnapshot(String userId, Double totalUserWealth, Double totalUserInvestment, Double totalUserGainLoss, Double totalUserGainLossPercentage, List<PortfolioSnapshotEntry> entries, LocalDate snapshotDate) {
        Optional<PortfolioSnapshotDocument> existing = portfolioSnapshotRepository.findByUserIdAndSnapshotDate(userId, snapshotDate);
        PortfolioSnapshotDocument snapshot;
        Double[] sessionDay = sumSessionDayGain(entries);
        
        if (existing.isPresent()) {
            // Update existing snapshot for the given date (Upsert)
            snapshot = existing.get();
            snapshot.setTotalUserWealth(totalUserWealth);
            snapshot.setTotalUserInvestment(totalUserInvestment);
            snapshot.setTotalUserGainLoss(totalUserGainLoss);
            snapshot.setTotalUserGainLossPercentage(totalUserGainLossPercentage);
            snapshot.setSessionDayGainLoss(sessionDay[0]);
            snapshot.setSessionDayGainLossPercentage(sessionDay[1]);
            snapshot.setPortfolios(entries);
            snapshot.setCreatedAt(LocalDateTime.now());
        } else {
            // Create new snapshot
            String snapshotId = java.util.UUID.randomUUID().toString();
            snapshot = PortfolioSnapshotDocument.builder()
                    .id(snapshotId)
                    .snapshotId(snapshotId)
                    .userId(userId)
                    .snapshotDate(snapshotDate)
                    .totalUserWealth(totalUserWealth)
                    .totalUserInvestment(totalUserInvestment)
                    .totalUserGainLoss(totalUserGainLoss)
                    .totalUserGainLossPercentage(totalUserGainLossPercentage)
                    .sessionDayGainLoss(sessionDay[0])
                    .sessionDayGainLossPercentage(sessionDay[1])
                    .portfolios(entries)
                    .createdAt(LocalDateTime.now())
                    .build();
        }

        portfolioSnapshotRepository.save(snapshot);
        log.info("Successfully upserted User-Centric snapshot for user {} on date {} totalWealth={}", userId, snapshotDate, totalUserWealth);
    }

    /** [0]=sum gain, [1]=pct vs sum of opens; nulls when no entry freeze. */
    private static Double[] sumSessionDayGain(List<PortfolioSnapshotEntry> entries) {
        if (entries == null || entries.isEmpty()) {
            return new Double[] { null, null };
        }
        double gain = 0.0;
        double prior = 0.0;
        int n = 0;
        for (PortfolioSnapshotEntry e : entries) {
            if (e.getSessionDayGainLoss() == null) {
                continue;
            }
            gain += e.getSessionDayGainLoss();
            if (e.getOpen() != null && e.getOpen() > 0) {
                prior += e.getOpen();
            }
            n++;
        }
        if (n == 0) {
            return new Double[] { null, null };
        }
        Double pct = prior > 0 ? (gain / prior) * 100.0 : null;
        return new Double[] { gain, pct };
    }

    public Optional<PortfolioSnapshotModel> getLatestFreshSnapshot(String userId, int freshnessMinutes) {
        LocalDate today = LocalDate.now();
        Optional<PortfolioSnapshotDocument> existing = portfolioSnapshotRepository.findByUserIdAndSnapshotDate(userId, today);
        
        if (existing.isPresent()) {
            PortfolioSnapshotDocument doc = existing.get();
            LocalDateTime cutoff = LocalDateTime.now().minusMinutes(freshnessMinutes);
            if (doc.getCreatedAt() != null && doc.getCreatedAt().isAfter(cutoff)) {
                return Optional.of(toModel(doc, null));
            } else {
                log.info("Snapshot for user {} is stale (older than {} minutes)", userId, freshnessMinutes);
            }
        }
        return Optional.empty();
    }

    @org.springframework.cache.annotation.CacheEvict(value = "portfolioHistory", allEntries = true)
    public void deleteSnapshot(String userId, LocalDate snapshotDate) {
        log.info("Deleting snapshot for user {} on date {}", userId, snapshotDate);
        Optional<PortfolioSnapshotDocument> existing = portfolioSnapshotRepository.findByUserIdAndSnapshotDate(userId, snapshotDate);
        existing.ifPresent(portfolioSnapshotRepository::delete);
    }

    @org.springframework.cache.annotation.Cacheable(value = "portfolioHistory", key = "#userId + '_' + (#portfolioId != null ? #portfolioId : 'all') + '_' + #timeFrame + '_v3'")
    public List<PortfolioSnapshotModel> getHistory(String userId, String portfolioId, String timeFrame) {
        String frame = timeFrame != null ? timeFrame.toUpperCase() : "1M";
        LocalDate today = LocalDate.now(IST);
        List<PortfolioSnapshotDocument> documents;

        if ("ALL".equals(frame)) {
            // No date filter → fetch entire history, already sorted ASC by DB
            documents = portfolioSnapshotRepository.findByUserIdOrderBySnapshotDateAsc(userId);
        } else if ("YTD".equals(frame)) {
            // Year to Date → January 1st of current year
            LocalDate fromDate = LocalDate.of(today.getYear(), 1, 1);
            documents = portfolioSnapshotRepository
                .findByUserIdAndSnapshotDateBetweenOrderBySnapshotDateAsc(userId, fromDate, today.plusDays(1));
        } else {
            // Real date math: 1M = exactly 1 calendar month ago; 1D = configurable lookback days
            java.time.Period period;
            if ("1D".equals(frame)) {
                // @Value default 10; guard 0 from non-Spring construction
                int days = oneDayLookbackDays > 0 ? oneDayLookbackDays : 10;
                period = java.time.Period.ofDays(days);
            } else {
                period = TIMEFRAME_PERIODS.getOrDefault(frame, java.time.Period.ofMonths(1));
            }
            LocalDate fromDate = today.minus(period);
            documents = portfolioSnapshotRepository
                .findByUserIdAndSnapshotDateBetweenOrderBySnapshotDateAsc(userId, fromDate, today.plusDays(1));
        }

        if (documents == null || documents.isEmpty()) {
            return Collections.emptyList();
        }

        return documents.stream()
                .filter(doc -> doc.getPortfolios() != null && !doc.getPortfolios().isEmpty())
                .map(doc -> toModel(doc, portfolioId))
                .filter(java.util.Objects::nonNull)
                .collect(Collectors.toList());
    }

    private PortfolioSnapshotModel toModel(PortfolioSnapshotDocument doc, String targetPortfolioId) {
        double wealth = 0.0, investment = 0.0, open = 0.0, high = 0.0, low = 0.0;
        double sessionDaySum = 0.0;
        double sessionPriorWealth = 0.0;
        int sessionDayCounted = 0;
        List<PortfolioSnapshotEntryModel> entryModels = new java.util.ArrayList<>();

        if (doc.getPortfolios() != null) {
            for (PortfolioSnapshotEntry p : doc.getPortfolios()) {
                boolean matches = targetPortfolioId == null || targetPortfolioId.equals(p.getPortfolioId());
                if (!matches) continue;

                double close = p.getClose() != null ? p.getClose() : 0.0;
                open       += p.getOpen()   != null ? p.getOpen()   : close;
                high       += p.getHigh()   != null ? p.getHigh()   : close;
                low        += p.getLow()    != null ? p.getLow()    : close;
                wealth     += close;
                investment += p.getTotalInvestment() != null ? p.getTotalInvestment() : 0.0;
                if (p.getSessionDayGainLoss() != null) {
                    sessionDaySum += p.getSessionDayGainLoss();
                    double entryOpen = p.getOpen() != null ? p.getOpen() : 0.0;
                    if (entryOpen > 0) {
                        sessionPriorWealth += entryOpen;
                    }
                    sessionDayCounted++;
                }
                entryModels.add(toEntryModel(p));
            }
        } else if (targetPortfolioId == null) {
            // Pre-V2 legacy snapshot — ONLY safe for global queries (all portfolios)
            double fallback = doc.getTotalUserWealth() != null ? doc.getTotalUserWealth() : 0.0;
            wealth = fallback; open = fallback; high = fallback; low = fallback;
            investment = doc.getTotalUserInvestment() != null ? doc.getTotalUserInvestment() : 0.0;
            if (doc.getSessionDayGainLoss() != null) {
                sessionDaySum = doc.getSessionDayGainLoss();
                sessionDayCounted = 1;
                sessionPriorWealth = open > 0 ? open : 0.0;
            }
        }

        if (wealth == 0.0 && investment == 0.0 && targetPortfolioId != null) {
            return null; // Caller will filter this out
        }

        double gainLoss    = wealth - investment;
        double gainLossPct = investment > 0 ? (gainLoss / investment) * 100.0 : 0.0;
        // Keep both ternary arms as Double — mixing double + Double unboxes null → NPE.
        Double sessionDay = sessionDayCounted > 0 ? Double.valueOf(sessionDaySum) : doc.getSessionDayGainLoss();
        Double sessionDayPct = null;
        if (sessionDay != null && sessionPriorWealth > 0) {
            sessionDayPct = (sessionDay / sessionPriorWealth) * 100.0;
        } else if (sessionDay != null && doc.getSessionDayGainLossPercentage() != null) {
            sessionDayPct = doc.getSessionDayGainLossPercentage();
        }

        return PortfolioSnapshotModel.builder()
                .snapshotId(doc.getSnapshotId())
                .snapshotDate(doc.getSnapshotDate())
                .totalUserWealth(round2(wealth))
                .totalUserWealthOpen(round2(open))
                .totalUserWealthHigh(round2(high))
                .totalUserWealthLow(round2(low))
                .totalUserInvestment(round2(investment))
                .totalUserGainLoss(round2(gainLoss))
                .totalUserGainLossPercentage(round2(gainLossPct))
                .sessionDayGainLoss(round2(sessionDay))
                .sessionDayGainLossPercentage(round2(sessionDayPct))
                .portfolios(entryModels)
                .createdAt(doc.getCreatedAt())
                .build();
    }

    /**
     * History/chart DTO: OHLC + investment/PnL only. Holdings stay on the Mongo
     * document for catch-up; {@code @JsonInclude(NON_NULL)} omits the field.
     */
    private PortfolioSnapshotEntryModel toEntryModel(PortfolioSnapshotEntry entry) {
        return PortfolioSnapshotEntryModel.builder()
                .portfolioId(entry.getPortfolioId())
                .portfolioName(entry.getPortfolioName())
                .brokerType(entry.getBrokerType())
                .open(round2(entry.getOpen()))
                .high(round2(entry.getHigh()))
                .low(round2(entry.getLow()))
                .close(round2(entry.getClose()))
                .totalInvestment(round2(entry.getTotalInvestment()))
                .totalGainLoss(round2(entry.getTotalGainLoss()))
                .totalGainLossPercentage(round2(entry.getTotalGainLossPercentage()))
                .sessionDayGainLoss(round2(entry.getSessionDayGainLoss()))
                .sessionDayGainLossPercentage(round2(entry.getSessionDayGainLossPercentage()))
                .build();
    }

    private static Double round2(Double value) {
        if (value == null) {
            return null;
        }
        return java.math.BigDecimal.valueOf(value)
                .setScale(2, java.math.RoundingMode.HALF_UP)
                .doubleValue();
    }
}
