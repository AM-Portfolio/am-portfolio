package com.portfolio.marketdata.service;

import com.portfolio.marketdata.model.MutualFundNavMark;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestTemplate;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.http.HttpMethod.POST;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class MutualFundNavServiceTest {
    @Test
    void parsesOfficialLatestFeedForGrowthAndReinvestmentIsins() {
        String report = """
                Scheme Code;ISIN Div Payout/ ISIN Growth;ISIN Div Reinvestment;Scheme Name;Plan;Option;Net Asset Value;Date
                147704;INF247L01999;-;Motilal Oswal Large and Midcap Fund;Direct Plan;Growth;40.6665;19-Nov-2026
                147705;INF247L01AB1;INF247L01AB2;Example IDCW Fund;Direct Plan;IDCW;12.3400;19-Nov-2026
                """;

        Map<String, MutualFundNavMark> result = MutualFundNavService.parseLatest(report);

        assertEquals(3, result.size());
        assertEquals(40.6665, result.get("INF247L01999").nav());
        assertEquals("Motilal Oswal Large and Midcap Fund", result.get("INF247L01999").schemeName());
        assertEquals(12.34, result.get("INF247L01AB1").nav());
        assertEquals(12.34, result.get("INF247L01AB2").nav());
        assertEquals(LocalDate.of(2026, 11, 19), result.get("INF247L01AB2").navDate());
    }

    @Test
    void parsesOfficialHistoricalFeedByEitherIsinColumn() {
        String legacy = """
                Scheme Code;Scheme Name;ISIN Div Payout/ISIN Growth;ISIN Div Reinvestment;Net Asset Value;Repurchase Price;Sale Price;Date
                147704;Motilal Oswal Large and Midcap Fund - Direct Plan Growth;INF247L01999;;40.5130;40.5130;40.5130;18-Nov-2026
                147705;Example IDCW Fund;INF247L01AB1;INF247L01AB2;12.3000;12.3000;12.3000;18-Nov-2026
                """;
        Map<String, Double> legacyResult = MutualFundNavService.parseHistory(legacy);
        assertEquals(40.513, legacyResult.get("INF247L01999"));
        assertEquals(12.3, legacyResult.get("INF247L01AB1"));
        assertEquals(12.3, legacyResult.get("INF247L01AB2"));

        String current = """
                Scheme Code;NAV Name;Plan;Option;ISIN Div Payout/ISIN Growth;ISIN Div Reinvestment;Net Asset Value;Date
                148921;Aditya Birla Sun Life Multi-Cap Fund-Direct Growth;Direct Plan;GROWTH;INF209KB1Y49;;23.45;19-Nov-2026
                147705;Example IDCW Fund;Direct Plan;IDCW;INF247L01AB1;INF247L01AB2;12.3000;18-Nov-2026
                """;
        Map<String, Double> currentResult = MutualFundNavService.parseHistory(current);
        assertEquals(23.45, currentResult.get("INF209KB1Y49"));
        assertEquals(12.3, currentResult.get("INF247L01AB1"));
        assertEquals(12.3, currentResult.get("INF247L01AB2"));
        assertNull(currentResult.get("DIRECT PLAN"));
    }

    @Test
    void parsesCatalogDetailAndPriorHistoryPoint() {
        MutualFundNavMark mark = MutualFundNavService.parseCatalogDetail(
                "INF247L01999",
                Map.of(
                        "isin", "INF247L01999",
                        "schemeName", "Motilal Oswal Large and Midcap Fund",
                        "nav", 40.6665,
                        "navDate", "2026-11-19",
                        "priceType", "EOD_NAV_NOT_LIVE_LTP"));
        assertNotNull(mark);
        assertEquals(40.6665, mark.nav());
        assertEquals(LocalDate.of(2026, 11, 19), mark.navDate());

        Double previous = MutualFundNavService.previousFromHistoryPoints(
                Map.of("points", List.of(
                        Map.of("date", "2026-11-17", "nav", 40.4),
                        Map.of("date", "2026-11-18", "nav", 40.513),
                        Map.of("date", "2026-11-19", "nav", 40.6665))),
                LocalDate.of(2026, 11, 19));
        assertEquals(40.513, previous);
    }

    @Test
    void parserBatchReturnsNavAndPreviousInOneCall() {
        RestTemplate restTemplate = new RestTemplate();
        MockRestServiceServer server = MockRestServiceServer.bindTo(restTemplate).build();
        MutualFundNavService service = new MutualFundNavService(restTemplate);
        ReflectionTestUtils.setField(service, "catalogBaseUrl", "http://am-parser.test");
        String isin = "INF247L01999";
        server.expect(requestTo("http://am-parser.test/v1/mutual-fund/nav/batch"))
                .andExpect(method(POST))
                .andRespond(withSuccess("""
                        {"source":"nse-master-download","marks":[
                          {"isin":"INF247L01999","schemeName":"Motilal Oswal Large and Midcap Fund",
                           "nav":40.6665,"previousNav":40.513,"navDate":"2026-11-19"}
                        ],"missing":[]}
                        """, MediaType.APPLICATION_JSON));

        try {
            Map<String, MutualFundNavMark> marks = service.getMarks(List.of(isin, isin));
            MutualFundNavMark mark = marks.get(isin);
            assertNotNull(mark);
            assertEquals(40.6665, mark.nav());
            assertEquals(40.513, mark.previousNav());
            assertTrue(mark.schemeName().contains("Motilal"));
            server.verify();
        } finally {
            service.stopLookupExecutor();
        }
    }

    @Test
    void emptyCatalogUrlReturnsNoMarks() {
        MutualFundNavService service = new MutualFundNavService(new RestTemplate());
        ReflectionTestUtils.setField(service, "catalogBaseUrl", "");
        try {
            assertTrue(service.getMarks(List.of("INF247L01999")).isEmpty());
        } finally {
            service.stopLookupExecutor();
        }
    }

    @Test
    void catalogMissingNavReturnsNull() {
        assertNull(MutualFundNavService.parseCatalogDetail(
                "INF247L01999", Map.of("isin", "INF247L01999", "schemeName", "Example")));
    }

    @Test
    void adjacentPreviousWithinThreeDaysAccepted() {
        LocalDate navDate = LocalDate.of(2026, 11, 19);
        Map<LocalDate, Map<String, Double>> history = Map.of(
                LocalDate.of(2026, 11, 18), Map.of("INF247L01999", 40.513),
                LocalDate.of(2026, 11, 10), Map.of("INF247L01999", 20.0));
        assertEquals(
                40.513,
                MutualFundNavService.selectAdjacentPreviousNav(navDate, "INF247L01999", history));
    }

    @Test
    void previousOlderThanThreeDaysRejected() {
        LocalDate navDate = LocalDate.of(2026, 11, 19);
        Map<LocalDate, Map<String, Double>> history = Map.of(
                LocalDate.of(2026, 11, 10), Map.of("INF247L01999", 20.0));
        assertNull(MutualFundNavService.selectAdjacentPreviousNav(navDate, "INF247L01999", history));
    }

    @Test
    void absurdOneDayMoveRejected() {
        assertTrue(MutualFundNavService.isPlausible1dMove(40.6665, 40.513));
        assertTrue(!MutualFundNavService.isPlausible1dMove(1649.9, 819.7));
    }

    @Test
    void catalogHistoryIgnoresFarPriorPoints() {
        Double previous = MutualFundNavService.previousFromHistoryPoints(
                Map.of("points", List.of(
                        Map.of("date", "2026-11-01", "nav", 20.0),
                        Map.of("date", "2026-11-18", "nav", 40.513),
                        Map.of("date", "2026-11-19", "nav", 40.6665))),
                LocalDate.of(2026, 11, 19));
        assertEquals(40.513, previous);

        assertNull(MutualFundNavService.previousFromHistoryPoints(
                Map.of("points", List.of(
                        Map.of("date", "2026-11-01", "nav", 20.0),
                        Map.of("date", "2026-11-19", "nav", 40.6665))),
                LocalDate.of(2026, 11, 19)));
    }

    @Test
    void parseBatchResponseMapsMarks() {
        Map<String, MutualFundNavMark> marks = MutualFundNavService.parseBatchResponse(Map.of(
                "marks", List.of(Map.of(
                        "isin", "INF247L01999",
                        "schemeName", "Motilal",
                        "nav", 40.6665,
                        "previousNav", 40.513,
                        "navDate", "2026-11-19"))));
        assertEquals(1, marks.size());
        assertEquals(40.513, marks.get("INF247L01999").previousNav());
    }
}
