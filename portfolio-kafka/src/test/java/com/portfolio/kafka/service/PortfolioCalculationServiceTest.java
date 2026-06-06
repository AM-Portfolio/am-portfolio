package com.portfolio.kafka.service;

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

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PortfolioCalculationServiceTest {

    @Mock private PortfolioHoldingsService holdingsService;
    @Mock private PortfolioCalculator calculator;
    @Mock private KafkaProducerService producerService;
    @InjectMocks private PortfolioCalculationService service;

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
        verify(producerService).sendPortfolioStreamMessage(any(), eq("corr"));
    }

    @Test void processCalculation_withoutPortfolioId_fetchesAll() {
        PortfolioHoldings ph = new PortfolioHoldings();
        ph.setEquityHoldings(List.of(holding("AAPL", 500.0)));
        when(holdingsService.getPortfolioHoldings("u1", TimeInterval.ONE_DAY)).thenReturn(ph);
        when(calculator.enrichHoldings(any())).thenReturn(ph.getEquityHoldings());
        when(calculator.calculateSummary(any(), anyDouble())).thenReturn(new PortfolioSummaryV1());

        service.processCalculation("u1", null, null);

        verify(holdingsService).getPortfolioHoldings("u1", TimeInterval.ONE_DAY);
        verify(producerService).sendPortfolioStreamMessage(any(), isNull());
    }

    @Test void processCalculation_nullHoldings_returnsEarly() {
        when(holdingsService.getPortfolioHoldings("u1", TimeInterval.ONE_DAY)).thenReturn(null);
        service.processCalculation("u1", null, null);
        verifyNoInteractions(calculator, producerService);
    }

    @Test void processCalculation_nullEquityHoldings_returnsEarly() {
        PortfolioHoldings ph = new PortfolioHoldings();
        ph.setEquityHoldings(null);
        when(holdingsService.getPortfolioHoldings("u1", TimeInterval.ONE_DAY)).thenReturn(ph);
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
        verify(producerService).sendPortfolioStreamMessage(cap.capture(), eq("corr"));
        assertEquals(1100.0, cap.getValue().getTotalValue());
        assertEquals("u1", cap.getValue().getUserId());
    }

    @Test void processCalculation_calculatorThrows_propagatesException() {
        PortfolioHoldings ph = new PortfolioHoldings();
        ph.setEquityHoldings(List.of(holding("AAPL", 1000.0)));
        when(holdingsService.getPortfolioHoldings("u1", TimeInterval.ONE_DAY)).thenReturn(ph);
        when(calculator.enrichHoldings(any())).thenThrow(new RuntimeException("calc error"));

        assertThrows(RuntimeException.class, () -> service.processCalculation("u1", null, null));
    }

    @Test void processCalculation_filtersNullInvestmentCosts() {
        PortfolioHoldings ph = new PortfolioHoldings();
        ph.setEquityHoldings(List.of(holding("A", 500.0), holding("B", null)));
        when(holdingsService.getPortfolioHoldings("u1", TimeInterval.ONE_DAY)).thenReturn(ph);
        when(calculator.enrichHoldings(any())).thenReturn(ph.getEquityHoldings());
        when(calculator.calculateSummary(any(), eq(500.0))).thenReturn(new PortfolioSummaryV1());

        service.processCalculation("u1", null, null);

        verify(calculator).calculateSummary(any(), eq(500.0));
    }
}
