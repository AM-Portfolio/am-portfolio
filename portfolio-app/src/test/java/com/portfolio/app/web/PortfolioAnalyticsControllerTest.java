package com.portfolio.app.web;

import com.am.common.amcommondata.model.PortfolioModelV1;
import com.am.common.amcommondata.service.PortfolioService;
import com.am.security.context.UserContext;
import com.portfolio.analytics.intelligence.PortfolioIntelligenceService;
import com.portfolio.analytics.service.providers.portfolio.PortfolioAnalyticsFacade;
import com.portfolio.api.PortfolioAnalyticsController;
import com.portfolio.api.exception.GlobalExceptionHandler;
import com.portfolio.api.security.PortfolioOwnerAssert;
import com.portfolio.model.analytics.intelligence.PortfolioIntelligenceResponse;
import com.portfolio.model.analytics.response.AdvancedAnalyticsResponse;
import com.portfolio.service.PortfolioDashboardService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Ownership gate for POST /v1/analytics/portfolio/{id}/** (P1 + intelligence).
 */
@WebMvcTest(PortfolioAnalyticsController.class)
@ContextConfiguration(classes = {
        PortfolioAnalyticsController.class,
        GlobalExceptionHandler.class,
        PortfolioOwnerAssert.class
})
@AutoConfigureMockMvc(addFilters = false)
@ActiveProfiles("web-test")
class PortfolioAnalyticsControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private PortfolioAnalyticsFacade portfolioAnalyticsFacade;

    @MockBean
    private PortfolioDashboardService portfolioDashboardService;

    @MockBean
    private PortfolioService portfolioService;

    @MockBean
    private PortfolioIntelligenceService portfolioIntelligenceService;

    @AfterEach
    void tearDown() {
        UserContext.clear();
    }

    @Test
    void advanced_nonOwner_returns403() throws Exception {
        UUID portfolioId = UUID.randomUUID();
        UserContext.setUserId("caller-user");

        PortfolioModelV1 portfolio = new PortfolioModelV1();
        portfolio.setId(portfolioId);
        portfolio.setOwner("other-owner");
        when(portfolioService.getPortfolioById(portfolioId)).thenReturn(portfolio);

        mockMvc.perform(post("/v1/analytics/portfolio/{portfolioId}/advanced", portfolioId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isForbidden());

        verify(portfolioAnalyticsFacade, never()).calculateAdvancedAnalytics(any());
    }

    @Test
    void advanced_owner_returns200() throws Exception {
        UUID portfolioId = UUID.randomUUID();
        String owner = "caller-user";
        UserContext.setUserId(owner);

        PortfolioModelV1 portfolio = new PortfolioModelV1();
        portfolio.setId(portfolioId);
        portfolio.setOwner(owner);
        when(portfolioService.getPortfolioById(portfolioId)).thenReturn(portfolio);
        when(portfolioAnalyticsFacade.calculateAdvancedAnalytics(any()))
                .thenReturn(new AdvancedAnalyticsResponse());

        mockMvc.perform(post("/v1/analytics/portfolio/{portfolioId}/advanced", portfolioId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isOk());

        verify(portfolioAnalyticsFacade).calculateAdvancedAnalytics(any());
    }

    @Test
    void advanced_missingPortfolio_returns404() throws Exception {
        UUID portfolioId = UUID.randomUUID();
        UserContext.setUserId("caller-user");
        when(portfolioService.getPortfolioById(portfolioId)).thenReturn(null);

        mockMvc.perform(post("/v1/analytics/portfolio/{portfolioId}/advanced", portfolioId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isNotFound());

        verify(portfolioAnalyticsFacade, never()).calculateAdvancedAnalytics(any());
    }

    @Test
    void intelligence_nonOwner_returns403() throws Exception {
        UUID portfolioId = UUID.randomUUID();
        UserContext.setUserId("caller-user");

        PortfolioModelV1 portfolio = new PortfolioModelV1();
        portfolio.setId(portfolioId);
        portfolio.setOwner("other-owner");
        when(portfolioService.getPortfolioById(portfolioId)).thenReturn(portfolio);

        mockMvc.perform(post("/v1/analytics/portfolio/{portfolioId}/intelligence", portfolioId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isForbidden());

        verify(portfolioIntelligenceService, never()).intelligence(anyString());
    }

    @Test
    void intelligence_owner_returns200() throws Exception {
        UUID portfolioId = UUID.randomUUID();
        String owner = "caller-user";
        UserContext.setUserId(owner);

        PortfolioModelV1 portfolio = new PortfolioModelV1();
        portfolio.setId(portfolioId);
        portfolio.setOwner(owner);
        when(portfolioService.getPortfolioById(portfolioId)).thenReturn(portfolio);
        when(portfolioIntelligenceService.intelligence(portfolioId.toString()))
                .thenReturn(PortfolioIntelligenceResponse.builder().portfolioId(portfolioId.toString()).build());

        mockMvc.perform(post("/v1/analytics/portfolio/{portfolioId}/intelligence", portfolioId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isOk());

        verify(portfolioIntelligenceService).intelligence(portfolioId.toString());
    }
}
