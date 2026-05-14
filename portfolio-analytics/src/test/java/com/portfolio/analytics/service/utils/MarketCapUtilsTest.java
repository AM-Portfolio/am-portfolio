package com.portfolio.analytics.service.utils;

import com.am.common.amcommondata.model.MarketCapType;
import com.portfolio.model.market.MarketData;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class MarketCapUtilsTest {

    private MarketData md(double price, long token) {
        MarketData m = new MarketData();
        m.setLastPrice(price);
        m.setInstrumentToken(token);
        return m;
    }

    @Test void classifyLargeCap() { assertEquals(MarketCapType.LARGE_CAP, MarketCapUtils.classifyMarketCapSize(50_000_000_000.0)); }
    @Test void classifyMidCap() { assertEquals(MarketCapType.MID_CAP, MarketCapUtils.classifyMarketCapSize(25_000_000_000.0)); }
    @Test void classifySmallCap() { assertEquals(MarketCapType.SMALL_CAP, MarketCapUtils.classifyMarketCapSize(5_000_000_000.0)); }
    @Test void classifyMidCapAtThreshold() { assertEquals(MarketCapType.MID_CAP, MarketCapUtils.classifyMarketCapSize(10_000_000_000.0)); }

    @Test void createMapping() {
        var m = MarketCapUtils.createMarketCapTypeMapping();
        assertEquals(3, m.size());
        assertEquals("Large Cap", m.get(MarketCapType.LARGE_CAP));
    }

    @Test void calcMarketCaps() {
        var caps = MarketCapUtils.calculateMarketCaps(Map.of("S", md(100.0, 1)));
        assertEquals(100.0 * MarketCapUtils.DEFAULT_SHARES_MULTIPLIER, caps.get("S"));
    }

    @Test void calcMarketCapsZeroPrice() { assertTrue(MarketCapUtils.calculateMarketCaps(Map.of("S", md(0.0, 1))).isEmpty()); }
    @Test void calcMarketCapsNullData() { var m = new HashMap<String,MarketData>(); m.put("S", null); assertTrue(MarketCapUtils.calculateMarketCaps(m).isEmpty()); }
    @Test void totalMarketCap() { assertEquals(300.0, MarketCapUtils.calculateTotalMarketCap(Map.of("A", 100.0, "B", 200.0))); }
    @Test void weightPct() { assertEquals(25.0, MarketCapUtils.calculateWeightPercentage(250.0, 1000.0)); }
    @Test void weightPctZero() { assertEquals(0.0, MarketCapUtils.calculateWeightPercentage(100.0, 0.0)); }

    @Test void topStocks() {
        var top = MarketCapUtils.getTopStocksByMarketCap(Map.of("A", 10.0, "B", 50.0, "C", 30.0), 2);
        assertEquals("B", top.get(0));
    }

    @Test void findByToken() { assertEquals("A", MarketCapUtils.findSymbolByInstrumentToken(111, List.of("A"), Map.of("A", md(1,111)))); }
    @Test void findByTokenMiss() { assertNull(MarketCapUtils.findSymbolByInstrumentToken(999, List.of("A"), Map.of("A", md(1,111)))); }

    @Test void classifySymbols() {
        var segs = MarketCapUtils.classifySymbolsByMarketCap(List.of("BIG"), Map.of("BIG", md(100.0, 1)));
        assertEquals("Large Cap", segs.get("BIG"));
    }

    @Test void groupBySegment() {
        var grouped = MarketCapUtils.groupMarketDataBySegment(
                Map.of("A", md(1,1), "B", md(2,2)),
                Map.of("A", "Large Cap", "B", "Small Cap"));
        assertEquals(2, grouped.size());
    }

    @Test void roundTest() { assertEquals(3.15, MarketCapUtils.roundToTwoDecimals(3.145)); }
}
