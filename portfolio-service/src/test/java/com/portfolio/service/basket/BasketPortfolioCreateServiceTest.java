package com.portfolio.service.basket;

import com.am.common.amcommondata.model.PortfolioModelV1;
import com.am.common.amcommondata.model.asset.equity.EquityModel;
import com.am.common.amcommondata.model.enums.PortfolioKind;
import com.am.common.amcommondata.repository.basket.BasketCreateIdempotencyRepository;
import com.am.common.amcommondata.service.PortfolioService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BasketPortfolioCreateServiceTest {

    @Mock
    private PortfolioService portfolioService;

    @Mock
    private AllocationLedgerService allocationLedgerService;

    @Mock
    private AllocationAvailabilityService allocationAvailabilityService;

    @Mock
    private BasketCreateIdempotencyRepository idempotencyRepository;

    @Mock
    private ObjectMapper objectMapper;

    @InjectMocks
    private BasketPortfolioCreateService basketPortfolioCreateService;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(basketPortfolioCreateService, "idempotencyRepository", idempotencyRepository);
    }

    @Test
    void create_heldLine_usesActiveAllocationsMapOnce() {
        UUID sourceId = UUID.randomUUID();
        PortfolioModelV1 source = PortfolioModelV1.builder()
                .id(sourceId)
                .owner("user-1")
                .portfolioKind(PortfolioKind.BROKER)
                .equityModels(List.of(
                        EquityModel.builder()
                                .isin("INE040A01034")
                                .symbol("HDFCBANK")
                                .quantity(20.0)
                                .avgBuyingPrice(100.0)
                                .currentPrice(105.0)
                                .build()))
                .build();

        when(portfolioService.getPortfolioById(sourceId)).thenReturn(source);
        when(allocationAvailabilityService.getActiveAllocations(sourceId.toString()))
                .thenReturn(new HashMap<>(Map.of("INE040A01034", 5.0)));
        when(allocationAvailabilityService.getAvailableQuantity(any(), eq("INE040A01034"), eq(20.0), eq(0.0)))
                .thenReturn(15.0);
        when(portfolioService.createBasketPortfolio(any())).thenAnswer(invocation -> {
            PortfolioModelV1 created = invocation.getArgument(0);
            created.setId(UUID.randomUUID());
            return created;
        });
        when(portfolioService.getPortfolioById(sourceId)).thenReturn(source);
        when(portfolioService.getAvailableQuantity(any(), eq("INE040A01034"), eq(20.0))).thenReturn(5.0);

        BasketPortfolioCreateService.CreateBasketRequest request =
                BasketPortfolioCreateService.CreateBasketRequest.builder()
                        .userId("user-1")
                        .sourcePortfolioId(sourceId.toString())
                        .etfIsin("INF204KB15I2")
                        .etfName("Bank ETF")
                        .lines(List.of(
                                BasketPortfolioCreateService.CreateBasketLine.builder()
                                        .status("HELD")
                                        .holdingIsin("INE040A01034")
                                        .holdingSymbol("HDFCBANK")
                                        .heldQuantity(10.0)
                                        .build()))
                        .build();

        BasketPortfolioCreateService.CreateBasketResponse response = basketPortfolioCreateService.create(request);

        assertThat(response.getMovedLines()).hasSize(1);
        assertThat(response.getMovedLines().get(0).getQuantity()).isEqualTo(10.0);
        assertThat(response.getAvailableAfter()).containsKey("INE040A01034");
        verify(allocationAvailabilityService).getActiveAllocations(sourceId.toString());
    }

    @Test
    void create_duplicateIdempotencyKey_returnsSameResponse() throws Exception {
        UUID sourceId = UUID.randomUUID();
        UUID basketId = UUID.randomUUID();
        PortfolioModelV1 source = PortfolioModelV1.builder()
                .id(sourceId)
                .owner("user-1")
                .portfolioKind(PortfolioKind.BROKER)
                .equityModels(List.of(
                        EquityModel.builder()
                                .isin("INE040A01034")
                                .symbol("HDFCBANK")
                                .quantity(20.0)
                                .avgBuyingPrice(100.0)
                                .currentPrice(105.0)
                                .build()))
                .build();

        when(portfolioService.getPortfolioById(sourceId)).thenReturn(source);
        when(allocationAvailabilityService.getActiveAllocations(sourceId.toString())).thenReturn(new HashMap<>());
        when(allocationAvailabilityService.getAvailableQuantity(any(), eq("INE040A01034"), eq(20.0), eq(0.0)))
                .thenReturn(20.0);
        when(portfolioService.createBasketPortfolio(any())).thenAnswer(invocation -> {
            PortfolioModelV1 created = invocation.getArgument(0);
            created.setId(basketId);
            return created;
        });
        when(portfolioService.getAvailableQuantity(any(), eq("INE040A01034"), eq(20.0))).thenReturn(20.0);
        when(idempotencyRepository.findById("idem-1")).thenReturn(Optional.empty());
        when(objectMapper.writeValueAsString(any())).thenReturn("{\"portfolioId\":\"" + basketId + "\"}");

        BasketPortfolioCreateService.CreateBasketRequest request =
                BasketPortfolioCreateService.CreateBasketRequest.builder()
                        .userId("user-1")
                        .sourcePortfolioId(sourceId.toString())
                        .idempotencyKey("idem-1")
                        .lines(List.of(
                                BasketPortfolioCreateService.CreateBasketLine.builder()
                                        .status("HELD")
                                        .holdingIsin("INE040A01034")
                                        .holdingSymbol("HDFCBANK")
                                        .heldQuantity(5.0)
                                        .build()))
                        .build();

        BasketPortfolioCreateService.CreateBasketResponse first = basketPortfolioCreateService.create(request);
        BasketPortfolioCreateService.CreateBasketResponse second = basketPortfolioCreateService.create(request);

        assertThat(second).isEqualTo(first);
        assertThat(second.getPortfolioId()).isEqualTo(basketId.toString());
        verify(portfolioService, times(1)).createBasketPortfolio(any());
    }
}
