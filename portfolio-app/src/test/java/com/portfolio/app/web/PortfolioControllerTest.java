package com.portfolio.app.web;

import com.am.common.amcommondata.model.PortfolioModelV1;
import com.am.common.amcommondata.service.PortfolioService;
import com.am.common.amcommondata.service.PortfolioSnapshotService;
import com.portfolio.api.PortfolioController;
import com.portfolio.api.exception.GlobalExceptionHandler;
import com.portfolio.service.PortfolioDashboardService;
import com.portfolio.service.scheduler.PortfolioHistoryScheduler;
import com.portfolio.service.scheduler.SnapshotCatchUpService;
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
import java.util.Collections;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import com.am.security.context.UserContext;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

/**
 * Unit tests for PortfolioController.
 * Moved to com.portfolio.app.web for consistent scanning.
 */
@WebMvcTest(PortfolioController.class)
@ContextConfiguration(classes = {PortfolioController.class, GlobalExceptionHandler.class})
@AutoConfigureMockMvc(addFilters = false)
@ActiveProfiles("web-test")
class PortfolioControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private PortfolioDashboardService portfolioDashboardService;

    @MockBean
    private PortfolioService portfolioService;

    @MockBean
    private PortfolioHistoryScheduler portfolioHistoryScheduler;

    @MockBean
    private PortfolioSnapshotService portfolioSnapshotService;

    @MockBean
    private SnapshotCatchUpService snapshotCatchUpService;

    @MockBean
    private com.portfolio.service.portfolio.PortfolioIntradayService portfolioIntradayService;

    @MockBean
    private com.portfolio.redis.service.ActiveMarketSymbolPublisher activeMarketSymbolPublisher;

    @MockBean
    private com.portfolio.service.resolver.PortfolioEquitySymbolNormalizer portfolioEquitySymbolNormalizer;

    @MockBean
    private com.portfolio.service.NewUserPortfolioFallbackService newUserPortfolioFallbackService;

    @MockBean
    private com.portfolio.api.security.PortfolioOwnerAssert portfolioOwnerAssert;

    @MockBean
    private com.portfolio.redis.service.PortfolioHoldingsRedisService portfolioHoldingsRedisService;

    @MockBean
    private com.portfolio.redis.service.PortfolioSummaryRedisService portfolioSummaryRedisService;

    @MockBean
    private com.portfolio.redis.service.PortfolioIntelligenceRedisService portfolioIntelligenceRedisService;

    @MockBean
    private com.portfolio.analytics.intelligence.AggregatePortfolioLoader aggregatePortfolioLoader;

    @MockBean
    private com.portfolio.service.portfolio.BrokerPortfolioDeleteService brokerPortfolioDeleteService;

    @AfterEach
    void tearDown() {
        UserContext.clear();
    }

    @Test
    void getPortfolioById_ValidUuid_ReturnsPortfolio() throws Exception {
        UUID portfolioId = UUID.randomUUID();
        PortfolioModelV1 model = new PortfolioModelV1();
        model.setId(portfolioId);
        model.setName("My Portfolio");

        when(portfolioService.getPortfolioById(portfolioId)).thenReturn(model);

        mockMvc.perform(get("/v1/portfolios/{portfolioId}", portfolioId.toString()))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.id").value(portfolioId.toString()))
                .andExpect(jsonPath("$.name").value("My Portfolio"));
    }

    @Test
    void getPortfolioById_MissingPortfolio_ReturnsNotFound() throws Exception {
        UUID portfolioId = UUID.randomUUID();
        when(portfolioService.getPortfolioById(portfolioId)).thenReturn(null);

        mockMvc.perform(get("/v1/portfolios/{portfolioId}", portfolioId.toString()))
                .andExpect(status().isNotFound());
    }

    @Test
    void getPortfolioById_InvalidUuid_ReturnsBadRequest() throws Exception {
        mockMvc.perform(get("/v1/portfolios/{portfolioId}", "not-a-uuid"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void getPortfolios_ReturnsList() throws Exception {
        String userId = "user-123";
        UserContext.setUserId(userId);
        PortfolioModelV1 p1 = new PortfolioModelV1();
        p1.setName("P1");
        
        when(portfolioService.getPortfoliosByUserId(userId)).thenReturn(Arrays.asList(p1));

        mockMvc.perform(get("/v1/portfolios"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].name").value("P1"));
    }

    @Test
    void getPortfolioBasicDetails_NoPortfolios_Returns200EmptyList() throws Exception {
        String userId = "empty-user";
        UserContext.setUserId(userId);
        when(newUserPortfolioFallbackService.listBasicPortfolios(userId))
                .thenReturn(Collections.emptyList());

        mockMvc.perform(get("/v1/portfolios/list"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void getPortfolioAnalysis_InvalidInterval_ReturnsBadRequest() throws Exception {
        String userId = "u1";
        UUID portfolioId = UUID.randomUUID();
        UserContext.setUserId(userId);
        when(newUserPortfolioFallbackService.resolveRequest(userId, portfolioId.toString()))
                .thenReturn(new com.portfolio.service.NewUserPortfolioFallbackService.DemoResolution(
                        userId, portfolioId.toString()));

        mockMvc.perform(get("/v1/portfolios/{id}/analysis", portfolioId.toString())
                .param("interval", "invalid"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void deletePortfolio_success_returns204_emptyBody() throws Exception {
        UUID portfolioId = UUID.randomUUID();
        String userId = "user-" + UUID.randomUUID();
        UserContext.setUserId(userId);
        doNothing().when(brokerPortfolioDeleteService)
                .deleteOwnedPortfolio(portfolioId.toString(), userId);

        mockMvc.perform(delete("/v1/portfolios/{portfolioId}", portfolioId.toString()))
                .andExpect(status().isNoContent())
                .andExpect(content().string(""));

        verify(brokerPortfolioDeleteService).deleteOwnedPortfolio(portfolioId.toString(), userId);
    }

    @Test
    void deletePortfolio_notFound_returns404Json() throws Exception {
        UUID portfolioId = UUID.randomUUID();
        String userId = "user-" + UUID.randomUUID();
        UserContext.setUserId(userId);
        doThrow(new ResponseStatusException(HttpStatus.NOT_FOUND, "Portfolio not found"))
                .when(brokerPortfolioDeleteService)
                .deleteOwnedPortfolio(portfolioId.toString(), userId);

        mockMvc.perform(delete("/v1/portfolios/{portfolioId}", portfolioId.toString()))
                .andExpect(status().isNotFound())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.message").value("Portfolio not found"));
    }

    @Test
    void deletePortfolio_forbidden_returns403Json() throws Exception {
        UUID portfolioId = UUID.randomUUID();
        String userId = "user-" + UUID.randomUUID();
        UserContext.setUserId(userId);
        doThrow(new ResponseStatusException(HttpStatus.FORBIDDEN, "Not owner of portfolio"))
                .when(brokerPortfolioDeleteService)
                .deleteOwnedPortfolio(portfolioId.toString(), userId);

        mockMvc.perform(delete("/v1/portfolios/{portfolioId}", portfolioId.toString()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(403))
                .andExpect(jsonPath("$.message").value("Not owner of portfolio"));
    }

    @Test
    void deletePortfolio_invalidUuid_returns400Json() throws Exception {
        String userId = "user-" + UUID.randomUUID();
        UserContext.setUserId(userId);
        doThrow(new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid portfolioId"))
                .when(brokerPortfolioDeleteService)
                .deleteOwnedPortfolio(eq("not-a-uuid"), eq(userId));

        mockMvc.perform(delete("/v1/portfolios/{portfolioId}", "not-a-uuid"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.message").value("Invalid portfolioId"));
    }

    @Test
    void deleteDemo_doesNotHitBrokerDelete() throws Exception {
        String userId = "user-" + UUID.randomUUID();
        UserContext.setUserId(userId);
        doNothing().when(newUserPortfolioFallbackService).dismissForUser(userId);

        mockMvc.perform(delete("/v1/portfolios/demo"))
                .andExpect(status().isNoContent());

        verify(newUserPortfolioFallbackService).dismissForUser(userId);
        verify(brokerPortfolioDeleteService, never()).deleteOwnedPortfolio(any(), any());
    }

    @Test
    void deletePortfolio_unauthorized_whenNoUserContext() throws Exception {
        UUID portfolioId = UUID.randomUUID();
        // UserContext cleared in @AfterEach / no setUserId → getUserIdOrThrow fails before service
        mockMvc.perform(delete("/v1/portfolios/{portfolioId}", portfolioId.toString()))
                .andExpect(status().is4xxClientError());

        verify(brokerPortfolioDeleteService, never()).deleteOwnedPortfolio(any(), any());
    }

    @Test
    void getPortfolios_emptyList_returns200EmptyArray() throws Exception {
        String userId = "user-" + UUID.randomUUID();
        UserContext.setUserId(userId);
        when(portfolioService.getPortfoliosByUserId(userId)).thenReturn(Collections.emptyList());

        mockMvc.perform(get("/v1/portfolios"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void getPortfolioById_stillWorksAfterDeleteEndpointAdded() throws Exception {
        UUID portfolioId = UUID.randomUUID();
        PortfolioModelV1 model = new PortfolioModelV1();
        model.setId(portfolioId);
        model.setName("StillReadable");
        when(portfolioService.getPortfolioById(portfolioId)).thenReturn(model);

        mockMvc.perform(get("/v1/portfolios/{portfolioId}", portfolioId.toString())
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(portfolioId.toString()))
                .andExpect(jsonPath("$.name").value("StillReadable"));
    }
}
