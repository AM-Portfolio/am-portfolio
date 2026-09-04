package com.portfolio.basket.service;

import com.portfolio.basket.model.EtfData;
import com.portfolio.basket.model.EtfHolding;
import com.portfolio.model.basket.ExposureResponse;
import com.portfolio.model.portfolio.EquityHoldings;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BasketAllocationServiceTest {

    @Mock
    private EnrichedEtfService enrichedEtfService;

    @InjectMocks
    private BasketAllocationService basketAllocationService;

    @Test
    void calculateCumulativeExposure_callsGetEnrichedEtfsBatchOnce() {
        List<EquityHoldings> holdings = List.of(
                EquityHoldings.builder().isin("INF001AAAAAAAA").symbol("AAA BEES").quantity(10.0).currentValue(1000.0).build(),
                EquityHoldings.builder().isin("INF002AAAAAAAA").symbol("BBB BEES").quantity(5.0).currentValue(500.0).build(),
                EquityHoldings.builder().isin("INF003AAAAAAAA").symbol("CCC BEES").quantity(5.0).currentValue(500.0).build());

        when(enrichedEtfService.getEnrichedEtfsBatch(anyList())).thenReturn(Map.of());

        ExposureResponse response = basketAllocationService.calculateCumulativeExposure(holdings);

        verify(enrichedEtfService, times(1)).getEnrichedEtfsBatch(anyList());
        assertThat(response.getStockExposure()).hasSize(3);
        assertThat(response.getStockExposure().stream().mapToDouble(s -> s.getDirectWeight()).sum())
                .isGreaterThan(99.0);
    }

    @Test
    void calculateCumulativeExposure_skipsBatchForEquityOnly() {
        List<EquityHoldings> holdings = List.of(
                EquityHoldings.builder().isin("INE001A01001").symbol("RELIANCE").quantity(10.0).currentValue(1000.0).build());

        ExposureResponse response = basketAllocationService.calculateCumulativeExposure(holdings);

        verify(enrichedEtfService, times(0)).getEnrichedEtfsBatch(anyList());
        assertThat(response.getStockExposure()).hasSize(1);
    }

    @Test
    void calculatePortfolioAllocation_reusesSingleBatch_notSecondLookThrough() {
        List<EquityHoldings> holdings = List.of(
                EquityHoldings.builder().isin("INF001AAAAAAAA").symbol("BANKBEES").quantity(10.0).currentValue(2000.0).build());

        when(enrichedEtfService.getEnrichedEtfsBatch(anyList())).thenReturn(Map.of());

        basketAllocationService.calculatePortfolioAllocation(holdings);

        verify(enrichedEtfService, times(1)).getEnrichedEtfsBatch(anyList());
    }

    @Test
    void calculateCumulativeExposure_looksThroughBatchEtf() {
        EtfHolding constituent = new EtfHolding();
        constituent.setIsin("INEHDFC");
        constituent.setSymbol("HDFCBANK");
        constituent.setSector("Financial");
        constituent.setWeight(100.0);
        EtfData etf = new EtfData();
        etf.setSymbol("BANKBEES");
        etf.setName("Bank BeES");
        etf.setHoldings(List.of(constituent));

        List<EquityHoldings> holdings = List.of(
                EquityHoldings.builder()
                        .isin("INFBANK")
                        .symbol("BANKBEES")
                        .quantity(10.0)
                        .currentValue(1000.0)
                        .build());

        when(enrichedEtfService.getEnrichedEtfsBatch(anyList()))
                .thenReturn(Map.of("INFBANK", etf));

        ExposureResponse response = basketAllocationService.calculateCumulativeExposure(holdings);

        verify(enrichedEtfService, times(1)).getEnrichedEtfsBatch(anyList());
        assertThat(response.getStockExposure()).hasSize(1);
        assertThat(response.getStockExposure().get(0).getSymbol()).isEqualTo("HDFCBANK");
        assertThat(response.getStockExposure().get(0).getIndirectWeight()).isEqualTo(100.0);
        assertThat(response.getStockExposure().get(0).getDirectWeight()).isEqualTo(0.0);
    }
}
