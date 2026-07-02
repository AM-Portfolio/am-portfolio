package com.portfolio.kafka.service;

import com.am.common.amcommondata.model.PortfolioModelV1;
import com.am.common.amcommondata.service.PortfolioService;
import com.portfolio.kafka.producer.KafkaProducerService;
import com.portfolio.model.TimeInterval;
import com.portfolio.model.events.PortfolioUpdateEvent;
import com.portfolio.model.portfolio.EquityHoldings;
import com.portfolio.model.portfolio.PortfolioHoldings;
import com.portfolio.model.portfolio.v1.PortfolioSummaryV1;
import com.portfolio.service.calculator.PortfolioCalculator;
import com.portfolio.service.portfolio.PortfolioHoldingsService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PortfolioCalculationServiceTest {

    @Mock private PortfolioHoldingsService holdingsService;
    @Mock private PortfolioCalculator calculator;
    @Mock private KafkaProducerService producerService;
    @Mock private PortfolioService portfolioService;
    @InjectMocks private PortfolioCalculationService service;

    private static final UUID P1_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");

    private EquityHoldings holding(String symbol, Double cost) {
        EquityHoldings h = new EquityHoldings();
        h.setSymbol(symbol);
        h.setInvestmentCost(cost);
        h.setQuantity(10.0);
        h.setAverageBuyingPrice(100.0);
        h.setCurrentPrice(110.0);
        return h;
    }

    @Test void processCalculation_withPortfolioId_fetchesSpecific() {
        PortfolioHoldings ph = new PortfolioHoldings();
        ph.setEquityHoldings(List.of(holding("AAPL", 1000.0)));
        when(holdingsService.getPortfolioHoldings("u1", "p1", TimeInterval.ONE_DAY)).thenReturn(ph);
        when(calculator.enrichHoldings(any())).thenReturn(ph.getEquityHoldings());
        when(calculator.calculateSummary(any(), anyDouble())).thenReturn(new PortfolioSummaryV1());

        service.processCalculation("u1", "p1", "corr");

        verify(holdingsService).getPortfolioHoldings("u1", "p1", TimeInterval.ONE_DAY);
        verify(producerService).sendMessage(any(), eq("corr"));
        verify(producerService).sendPortfolioStreamMessage(any(), eq("corr"));
        verifyNoInteractions(portfolioService);
    }

    @Test void processCalculation_withoutPortfolioId_publishesPerPortfolio() {
        PortfolioModelV1 portfolio = PortfolioModelV1.builder().id(P1_ID).build();
        when(portfolioService.getPortfoliosByUserId("u1")).thenReturn(List.of(portfolio));

        PortfolioHoldings ph = new PortfolioHoldings();
        ph.setEquityHoldings(List.of(holding("AAPL", 500.0)));
        when(holdingsService.getPortfolioHoldings("u1", P1_ID.toString(), TimeInterval.ONE_DAY)).thenReturn(ph);
        when(calculator.enrichHoldings(any())).thenReturn(ph.getEquityHoldings());
        when(calculator.calculateSummary(any(), anyDouble())).thenReturn(new PortfolioSummaryV1());

        service.processCalculation("u1", null, null);

        verify(portfolioService).getPortfoliosByUserId("u1");
        verify(holdingsService).getPortfolioHoldings("u1", P1_ID.toString(), TimeInterval.ONE_DAY);
        verify(producerService).sendMessage(any(), isNull());
        verify(producerService).sendPortfolioStreamMessage(any(), isNull());
    }

    @Test void processCalculation_withoutPortfolioId_noPortfolios_returnsEarly() {
        when(portfolioService.getPortfoliosByUserId("u1")).thenReturn(List.of());
        service.processCalculation("u1", null, null);
        verifyNoInteractions(holdingsService, calculator, producerService);
    }

    @Test void processCalculation_nullHoldings_returnsEarly() {
        when(portfolioService.getPortfoliosByUserId("u1"))
                .thenReturn(List.of(PortfolioModelV1.builder().id(P1_ID).build()));
        when(holdingsService.getPortfolioHoldings("u1", P1_ID.toString(), TimeInterval.ONE_DAY)).thenReturn(null);
        service.processCalculation("u1", null, null);
        verifyNoInteractions(calculator, producerService);
    }

    @Test void processCalculation_nullEquityHoldings_returnsEarly() {
        when(portfolioService.getPortfoliosByUserId("u1"))
                .thenReturn(List.of(PortfolioModelV1.builder().id(P1_ID).build()));
        PortfolioHoldings ph = new PortfolioHoldings();
        ph.setEquityHoldings(null);
        when(holdingsService.getPortfolioHoldings("u1", P1_ID.toString(), TimeInterval.ONE_DAY)).thenReturn(ph);
        service.processCalculation("u1", null, null);
        verifyNoInteractions(calculator, producerService);
    }

    @Test void processCalculation_eventContainsSummaryData() {
        PortfolioHoldings ph = new PortfolioHoldings();
        ph.setEquityHoldings(List.of(holding("AAPL", 1000.0)));
        when(holdingsService.getPortfolioHoldings("u1", "p1", TimeInterval.ONE_DAY)).thenReturn(ph);
        when(calculator.enrichHoldings(any())).thenReturn(ph.getEquityHoldings());

        PortfolioSummaryV1 summary = new PortfolioSummaryV1();
        summary.setCurrentValue(1100.0);
        summary.setInvestmentValue(1000.0);
        summary.setTotalGainLoss(100.0);
        when(calculator.calculateSummary(any(), anyDouble())).thenReturn(summary);

        service.processCalculation("u1", "p1", "corr");

        ArgumentCaptor<PortfolioUpdateEvent> cap = ArgumentCaptor.forClass(PortfolioUpdateEvent.class);
        verify(producerService).sendMessage(cap.capture(), eq("corr"));
        verify(producerService).sendPortfolioStreamMessage(any(), eq("corr"));
        assertEquals(1100.0, cap.getValue().getTotalValue());
        assertEquals("u1", cap.getValue().getUserId());
        assertEquals("p1", cap.getValue().getPortfolioId());
    }

    @Test void processCalculation_calculatorThrows_propagatesException() {
        when(portfolioService.getPortfoliosByUserId("u1"))
                .thenReturn(List.of(PortfolioModelV1.builder().id(P1_ID).build()));
        PortfolioHoldings ph = new PortfolioHoldings();
        ph.setEquityHoldings(List.of(holding("AAPL", 1000.0)));
        when(holdingsService.getPortfolioHoldings("u1", P1_ID.toString(), TimeInterval.ONE_DAY)).thenReturn(ph);
        when(calculator.enrichHoldings(any())).thenThrow(new RuntimeException("calc error"));

        assertThrows(RuntimeException.class, () -> service.processCalculation("u1", null, null));
    }

    @Test void processCalculation_filtersNullInvestmentCosts() {
        when(portfolioService.getPortfoliosByUserId("u1"))
                .thenReturn(List.of(PortfolioModelV1.builder().id(P1_ID).build()));
        PortfolioHoldings ph = new PortfolioHoldings();
        ph.setEquityHoldings(List.of(holding("A", 500.0), holding("B", null)));
        when(holdingsService.getPortfolioHoldings("u1", P1_ID.toString(), TimeInterval.ONE_DAY)).thenReturn(ph);
        when(calculator.enrichHoldings(any())).thenReturn(ph.getEquityHoldings());
        when(calculator.calculateSummary(any(), eq(500.0))).thenReturn(new PortfolioSummaryV1());

        service.processCalculation("u1", null, null);

        verify(calculator).calculateSummary(any(), eq(500.0));
    }
}
