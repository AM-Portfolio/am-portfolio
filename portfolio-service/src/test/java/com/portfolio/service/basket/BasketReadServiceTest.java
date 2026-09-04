package com.portfolio.service.basket;

import com.am.common.amcommondata.model.PortfolioModelV1;
import com.am.common.amcommondata.model.asset.equity.EquityModel;
import com.am.common.amcommondata.model.enums.PortfolioKind;
import com.am.common.amcommondata.service.PortfolioService;
import com.portfolio.marketdata.service.MarketDataService;
import com.portfolio.model.market.MarketData;
import com.portfolio.service.basket.dto.BasketDetailDto;
import com.portfolio.service.basket.dto.BasketSummaryDto;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BasketReadServiceTest {

    @Mock
    private PortfolioService portfolioService;

    @Mock
    private MarketDataService marketDataService;

    @InjectMocks
    private BasketReadService basketReadService;

    @Test
    void findBasketsByOwner_returnsBasketSummaries() {
        UUID id = UUID.randomUUID();
        PortfolioModelV1 basket = PortfolioModelV1.builder()
                .id(id)
                .etfName("Nifty IT")
                .etfIsin("INF204KB15I2")
                .status("ACTIVE")
                .portfolioKind(PortfolioKind.BASKET)
                .equityModels(List.of(EquityModel.builder().symbol("TCS").build()))
                .totalValue(10000.0)
                .investmentAmount(9000.0)
                .createdAt(LocalDateTime.now())
                .build();

        when(portfolioService.getPortfoliosByUserId("user-1")).thenReturn(List.of(basket));

        List<BasketSummaryDto> result = basketReadService.findBasketsByOwner("user-1", null);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getId()).isEqualTo(id.toString());
        assertThat(result.get(0).getEtfName()).isEqualTo("Nifty IT");
        assertThat(result.get(0).getAssetCount()).isEqualTo(1);
    }

    @Test
    void findBasketsByOwner_requiresUserId() {
        assertThatThrownBy(() -> basketReadService.findBasketsByOwner("", null))
                .isInstanceOf(ResponseStatusException.class);
    }

    @Test
    void getBasketDetail_buildsValuationFromLivePrice() {
        UUID basketId = UUID.randomUUID();
        PortfolioModelV1 basket = PortfolioModelV1.builder()
                .id(basketId)
                .owner("user-1")
                .portfolioKind(PortfolioKind.BASKET)
                .etfName("Bank ETF")
                .etfIsin("INF204KB15I2")
                .status("ACTIVE")
                .equityModels(List.of(
                        EquityModel.builder()
                                .symbol("HDFCBANK")
                                .isin("INE040A01034")
                                .quantity(10.0)
                                .avgBuyingPrice(100.0)
                                .currentPrice(95.0)
                                .status("HELD")
                                .build()))
                .createdAt(LocalDateTime.now())
                .build();

        when(portfolioService.getPortfolioById(basketId)).thenReturn(basket);
        when(marketDataService.getMarketData(anyList())).thenReturn(Map.of(
                "HDFCBANK", MarketData.builder()
                        .symbol("HDFCBANK")
                        .lastPrice(110.0)
                        .timestamp(Instant.now())
                        .build()));

        BasketDetailDto detail = basketReadService.getBasketDetail(basketId.toString(), "user-1");

        assertThat(detail.getId()).isEqualTo(basketId.toString());
        assertThat(detail.getTotalInvestedValue()).isEqualTo(1000.0);
        assertThat(detail.getTotalCurrentValue()).isEqualTo(1100.0);
        assertThat(detail.getLines()).hasSize(1);
        assertThat(detail.getLines().get(0).getCurrentPrice()).isEqualTo(110.0);
    }
}
