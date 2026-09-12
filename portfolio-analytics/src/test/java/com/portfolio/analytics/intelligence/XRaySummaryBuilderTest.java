package com.portfolio.analytics.intelligence;

import com.portfolio.model.analytics.intelligence.XRayDto;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class XRaySummaryBuilderTest {

    private final XRaySummaryBuilder builder = new XRaySummaryBuilder();

    @Test
    void groupsWeightAndValue_andSetsTotalValue() {
        PortfolioIntelligenceSnapshot snapshot = PortfolioIntelligenceSnapshot.builder()
                .portfolioId("fixture")
                .holdings(List.of(
                        holding("HDFCBANK", 250_000, 25.00, "Banking", "Banks", "LARGE_CAP"),
                        holding("ICICIBANK", 100_000, 10.00, "Banking", "Banks", "LARGE_CAP"),
                        holding("TCS", 150_000, 15.00, "IT", "Software", "LARGE_CAP"),
                        holding("INFY", 50_000, 5.00, "IT", "Software", "LARGE_CAP"),
                        holding("RELIANCE", 200_000, 20.00, "Energy", "Oil", "LARGE_CAP"),
                        holding("MIDA", 100_000, 10.00, "Pharma", "Drugs", "MID_CAP"),
                        holding("SMALLB", 150_000, 15.00, "Pharma", "Drugs", "SMALL_CAP")
                ))
                .totalValue(1_000_000)
                .build();

        XRayDto xray = builder.build(snapshot);

        assertEquals(1_000_000.0, xray.getTotalValue());

        Map<String, XRayDto.WeightSliceDto> sectors = byName(xray.getSectorWeights());
        assertEquals(35.0, sectors.get("Banking").getWeightPct());
        assertEquals(350_000.0, sectors.get("Banking").getValue());
        assertEquals(20.0, sectors.get("IT").getWeightPct());
        assertEquals(200_000.0, sectors.get("IT").getValue());
        assertEquals(25.0, sectors.get("Pharma").getWeightPct());
        assertEquals(250_000.0, sectors.get("Pharma").getValue());

        Map<String, XRayDto.WeightSliceDto> industries = byName(xray.getIndustryWeights());
        assertEquals(35.0, industries.get("Banks").getWeightPct());
        assertEquals(350_000.0, industries.get("Banks").getValue());

        Map<String, XRayDto.WeightSliceDto> caps = byName(xray.getMarketCapWeights());
        assertEquals(75.0, caps.get("LARGE_CAP").getWeightPct());
        assertEquals(750_000.0, caps.get("LARGE_CAP").getValue());
        assertEquals(10.0, caps.get("MID_CAP").getWeightPct());
        assertEquals(100_000.0, caps.get("MID_CAP").getValue());
        assertEquals(15.0, caps.get("SMALL_CAP").getWeightPct());
        assertEquals(150_000.0, caps.get("SMALL_CAP").getValue());

        assertEquals("Banking", xray.getSectorWeights().get(0).getName());
        assertNotNull(xray.getSectorWeights().get(0).getValue());
    }

    @Test
    void blankKeysBecomeUnknown() {
        PortfolioIntelligenceSnapshot snapshot = PortfolioIntelligenceSnapshot.builder()
                .portfolioId("fixture")
                .holdings(List.of(
                        holding("A", 60, 60, null, "  ", null),
                        holding("B", 40, 40, "", null, "")
                ))
                .totalValue(100)
                .build();

        XRayDto xray = builder.build(snapshot);
        assertEquals(100.0, xray.getTotalValue());
        assertEquals(1, xray.getSectorWeights().size());
        assertEquals("Unknown", xray.getSectorWeights().get(0).getName());
        assertEquals(100.0, xray.getSectorWeights().get(0).getWeightPct());
        assertEquals(100.0, xray.getSectorWeights().get(0).getValue());
    }

    private static Map<String, XRayDto.WeightSliceDto> byName(List<XRayDto.WeightSliceDto> slices) {
        return slices.stream().collect(Collectors.toMap(XRayDto.WeightSliceDto::getName, s -> s));
    }

    private static PortfolioIntelligenceSnapshot.Holding holding(
            String symbol,
            double value,
            double weightPct,
            String sector,
            String industry,
            String cap) {
        return PortfolioIntelligenceSnapshot.Holding.builder()
                .symbol(symbol)
                .value(value)
                .weightPct(weightPct)
                .sector(sector)
                .industry(industry)
                .marketCap(cap)
                .build();
    }
}
