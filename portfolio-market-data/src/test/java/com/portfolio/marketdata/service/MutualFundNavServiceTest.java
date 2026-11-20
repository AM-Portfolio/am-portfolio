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
import static org.springframework.http.HttpMethod.GET;
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
        String report = """
                Scheme Code;Scheme Name;ISIN Div Payout/ISIN Growth;ISIN Div Reinvestment;Net Asset Value;Repurchase Price;Sale Price;Date
                147704;Motilal Oswal Large and Midcap Fund - Direct Plan Growth;INF247L01999;;40.5130;40.5130;40.5130;18-Nov-2026
                147705;Example IDCW Fund;INF247L01999;INF247L01AB1;12.3000;12.3000;12.3000;18-Nov-2026
                """;

        Map<String, Double> result = MutualFundNavService.parseHistory(report);

        assertEquals(40.513, result.get("INF247L01999"));
        assertEquals(12.3, result.get("INF247L01AB1"));
    }

    @Test
    void fetchesOfficialLatestFeedAndAddsPreviousNavAsynchronously() throws Exception {
        RestTemplate restTemplate = new RestTemplate();
        MockRestServiceServer server = MockRestServiceServer.bindTo(restTemplate).build();
        MutualFundNavService service = new MutualFundNavService(restTemplate);
        ReflectionTestUtils.setField(service, "latestUrl", "http://amfi.test/latest");
        ReflectionTestUtils.setField(service, "historyUrl", "http://amfi.test/history");
        String isin = "INF247L01999";
        server.expect(requestTo("http://amfi.test/latest"))
                .andExpect(method(GET))
                .andRespond(withSuccess("""
                        Scheme Code;ISIN Growth;ISIN Reinvestment;Scheme Name;Plan;Option;NAV;Date
                        147704;INF247L01999;-;Motilal Oswal Large and Midcap Fund;Direct Plan;Growth;40.6665;19-Nov-2026
                        """, MediaType.TEXT_PLAIN));
        server.expect(requestTo("http://amfi.test/history?frmdt=18-Nov-2026&todt=18-Nov-2026"))
                .andExpect(method(GET))
                .andRespond(withSuccess("""
                        Scheme Code;Scheme Name;ISIN Growth;ISIN Reinvestment;NAV;Repurchase;Sale;Date
                        147704;Motilal Oswal Large and Midcap Fund;INF247L01999;;40.5130;40.5130;40.5130;18-Nov-2026
                        """, MediaType.TEXT_PLAIN));

        try {
            Map<String, MutualFundNavMark> initial = service.getMarks(List.of(isin));
            assertEquals(40.6665, initial.get(isin).nav());
            MutualFundNavMark resolved = service.getMark(isin);
            long deadline = System.nanoTime() + java.util.concurrent.TimeUnit.SECONDS.toNanos(3);
            while (resolved.previousNav() == null && System.nanoTime() < deadline) {
                Thread.sleep(10);
                resolved = service.getMark(isin);
            }
            assertNotNull(resolved);
            assertEquals(40.513, resolved.previousNav());
            server.verify();
        } finally {
            service.stopLookupExecutor();
        }
    }
}
