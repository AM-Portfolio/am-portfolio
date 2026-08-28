package com.am.common.amcommondata.service;

import com.am.common.amcommondata.document.portfolio.HoldingSnapshotItem;
import com.am.common.amcommondata.document.portfolio.PortfolioSnapshotDocument;
import com.am.common.amcommondata.document.portfolio.PortfolioSnapshotEntry;
import com.am.common.amcommondata.model.PortfolioSnapshotEntryModel;
import com.am.common.amcommondata.model.PortfolioSnapshotModel;
import com.am.common.amcommondata.repository.portfolio.PortfolioSnapshotRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PortfolioSnapshotServiceHistoryTest {

    @Mock
    private PortfolioSnapshotRepository portfolioSnapshotRepository;

    @InjectMocks
    private PortfolioSnapshotService portfolioSnapshotService;

    @Test
    void getHistory_omitsHoldingsFromApiModel() {
        HoldingSnapshotItem holding = HoldingSnapshotItem.builder()
                .symbol("DELTACORP")
                .isin("INE124G01033")
                .quantity(8.0)
                .avgBuyPrice(121.69)
                .build();

        PortfolioSnapshotEntry entry = PortfolioSnapshotEntry.builder()
                .portfolioId("065054d6-07af-445e-a795-755d872841c0")
                .portfolioName("Zerodha")
                .brokerType("ZERODHA")
                .open(928805.07)
                .high(928805.07)
                .low(928805.07)
                .close(928805.07)
                .totalInvestment(854184.61)
                .totalGainLoss(74620.46)
                .totalGainLossPercentage(8.73)
                .holdings(List.of(holding))
                .build();

        PortfolioSnapshotDocument doc = PortfolioSnapshotDocument.builder()
                .snapshotId("snap-1")
                .userId("user-1")
                .snapshotDate(LocalDate.of(2026, 8, 22))
                .portfolios(List.of(entry))
                .build();

        when(portfolioSnapshotRepository.findByUserIdAndSnapshotDateBetweenOrderBySnapshotDateAsc(
                eq("user-1"), any(LocalDate.class), any(LocalDate.class)))
                .thenReturn(List.of(doc));

        List<PortfolioSnapshotModel> history = portfolioSnapshotService.getHistory(
                "user-1", "065054d6-07af-445e-a795-755d872841c0", "1M");

        assertEquals(1, history.size());
        assertEquals(1, history.get(0).getPortfolios().size());
        PortfolioSnapshotEntryModel mapped = history.get(0).getPortfolios().get(0);
        assertEquals("Zerodha", mapped.getPortfolioName());
        assertEquals(928805.07, mapped.getClose());
        assertNotNull(entry.getHoldings());
        assertEquals(1, entry.getHoldings().size());
        assertNull(mapped.getHoldings());
    }

    @Test
    void getHistory_serializesMoneyToTwoDecimals() throws Exception {
        PortfolioSnapshotEntry entry = PortfolioSnapshotEntry.builder()
                .portfolioId("065054d6-07af-445e-a795-755d872841c0")
                .portfolioName("Zerodha")
                .brokerType("ZERODHA")
                .open(933243.6900000001)
                .high(933243.6900000001)
                .low(933243.6900000001)
                .close(933243.6900000001)
                .totalInvestment(854184.6100000002)
                .totalGainLoss(79059.07999999984)
                .totalGainLossPercentage(9.255502741965794)
                .build();

        PortfolioSnapshotDocument doc = PortfolioSnapshotDocument.builder()
                .snapshotId("snap-1")
                .userId("user-1")
                .snapshotDate(LocalDate.of(2026, 8, 12))
                .portfolios(List.of(entry))
                .build();

        when(portfolioSnapshotRepository.findByUserIdAndSnapshotDateBetweenOrderBySnapshotDateAsc(
                eq("user-1"), any(LocalDate.class), any(LocalDate.class)))
                .thenReturn(List.of(doc));

        List<PortfolioSnapshotModel> history = portfolioSnapshotService.getHistory(
                "user-1", "065054d6-07af-445e-a795-755d872841c0", "1M");

        com.fasterxml.jackson.databind.ObjectMapper mapper =
                new com.fasterxml.jackson.databind.ObjectMapper()
                        .registerModule(new com.fasterxml.jackson.datatype.jsr310.JavaTimeModule())
                        .disable(com.fasterxml.jackson.databind.SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        String json = mapper.writeValueAsString(history.get(0));

        org.junit.jupiter.api.Assertions.assertFalse(json.contains("000000"));
        org.junit.jupiter.api.Assertions.assertTrue(json.contains("\"close\":933243.69"));
        org.junit.jupiter.api.Assertions.assertTrue(json.contains("\"totalInvestment\":854184.61"));
        org.junit.jupiter.api.Assertions.assertTrue(json.contains("\"totalUserGainLossPercentage\":9.26"));

        PortfolioSnapshotModel withUserId = history.get(0).toBuilder()
                .userId("user-1")
                .build();
        String jsonWithUser = mapper.writeValueAsString(withUserId);
        org.junit.jupiter.api.Assertions.assertFalse(json.contains("userId"));
        org.junit.jupiter.api.Assertions.assertFalse(json.contains("user-1"));
        org.junit.jupiter.api.Assertions.assertFalse(jsonWithUser.contains("userId"));
        org.junit.jupiter.api.Assertions.assertFalse(jsonWithUser.contains("user-1"));
    }
}
