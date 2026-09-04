package com.portfolio.app.web;

import com.am.common.amcommondata.service.PortfolioService;
import com.portfolio.api.BasketController;
import com.portfolio.api.exception.GlobalExceptionHandler;
import com.portfolio.basket.service.BasketAllocationService;
import com.portfolio.basket.service.BasketCatalogService;
import com.portfolio.basket.service.BasketEngineFacade;
import com.portfolio.basket.service.BasketEngineService;
import com.portfolio.basket.service.HoldingSectorEnricher;
import com.portfolio.service.basket.AllocationLedgerService;
import com.portfolio.service.basket.BasketPortfolioCreateService;
import com.portfolio.service.basket.BasketReadService;
import com.portfolio.service.basket.dto.BasketDetailDto;
import com.portfolio.service.basket.dto.BasketSummaryDto;
import com.portfolio.service.portfolio.PortfolioHoldingsService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Arrays;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Unit tests for BasketController.
 * Moved to com.portfolio.app.web for consistent scanning.
 */
@WebMvcTest(BasketController.class)
@ContextConfiguration(classes = {BasketController.class, GlobalExceptionHandler.class})
@AutoConfigureMockMvc(addFilters = false)
@ActiveProfiles("web-test")
class BasketControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private BasketEngineService basketService;

    @MockBean
    private BasketEngineFacade basketEngineFacade;

    @MockBean
    private BasketAllocationService basketAllocationService;

    @MockBean
    private BasketCatalogService basketCatalogService;

    @MockBean
    private PortfolioHoldingsService portfolioHoldingsService;

    @MockBean
    private HoldingSectorEnricher holdingSectorEnricher;

    @MockBean
    private BasketPortfolioCreateService basketPortfolioCreateService;

    @MockBean
    private PortfolioService portfolioService;

    @MockBean
    private AllocationLedgerService allocationLedgerService;

    @MockBean
    private BasketReadService basketReadService;

    @Test
    void getMyBaskets_ReturnsSummaries() throws Exception {
        String userId = "user-123";
        List<BasketSummaryDto> summaries = Arrays.asList(
                BasketSummaryDto.builder()
                        .id(UUID.randomUUID().toString())
                        .etfName("Nifty 50 ETF")
                        .etfIsin("INF204KB14Y2")
                        .status("ACTIVE")
                        .assetCount(10)
                        .totalValue(50000.0)
                        .build());

        when(basketReadService.findBasketsByOwner(userId, null)).thenReturn(summaries);

        mockMvc.perform(get("/v1/basket/my").param("userId", userId))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].etfName").value("Nifty 50 ETF"))
                .andExpect(jsonPath("$[0].etfIsin").value("INF204KB14Y2"))
                .andExpect(jsonPath("$[0].status").value("ACTIVE"))
                .andExpect(jsonPath("$[0].assetCount").value(10))
                .andExpect(jsonPath("$[0].totalValue").value(50000.0));
    }

    @Test
    void getBasketDetail_InvalidUuid_ReturnsBadRequest() throws Exception {
        mockMvc.perform(get("/v1/basket/{basketId}", "not-a-uuid")
                        .param("userId", "user-123"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void getBasketDetail_ValidUuid_DelegatesToReadService() throws Exception {
        String userId = "user-123";
        UUID basketId = UUID.randomUUID();
        BasketDetailDto detail = BasketDetailDto.builder()
                .id(basketId.toString())
                .name("My Basket")
                .etfName("Nifty 50 ETF")
                .etfIsin("INF204KB14Y2")
                .status("ACTIVE")
                .totalCurrentValue(52000.0)
                .investmentAmount(50000.0)
                .build();

        when(basketReadService.getBasketDetail(basketId.toString(), userId)).thenReturn(detail);

        mockMvc.perform(get("/v1/basket/{basketId}", basketId.toString())
                        .param("userId", userId))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.id").value(basketId.toString()))
                .andExpect(jsonPath("$.name").value("My Basket"))
                .andExpect(jsonPath("$.etfName").value("Nifty 50 ETF"))
                .andExpect(jsonPath("$.status").value("ACTIVE"));

        verify(basketReadService).getBasketDetail(eq(basketId.toString()), eq(userId));
    }
}
