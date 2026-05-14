package com.portfolio.app.web;

import com.am.common.amcommondata.model.PortfolioModelV1;
import com.am.common.amcommondata.service.PortfolioService;
import com.portfolio.api.PortfolioController;
import com.portfolio.api.exception.GlobalExceptionHandler;
import com.portfolio.service.PortfolioDashboardService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Arrays;
import java.util.Collections;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Unit tests for PortfolioController.
 * Moved to com.portfolio.app.web for consistent scanning.
 */
@WebMvcTest(PortfolioController.class)
@ContextConfiguration(classes = {PortfolioController.class, GlobalExceptionHandler.class})
@ActiveProfiles("web-test")
class PortfolioControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private PortfolioDashboardService portfolioDashboardService;

    @MockBean
    private PortfolioService portfolioService;

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
    void getPortfolioById_InvalidUuid_ReturnsBadRequest() throws Exception {
        mockMvc.perform(get("/v1/portfolios/{portfolioId}", "not-a-uuid"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void getPortfolios_ReturnsList() throws Exception {
        String userId = "user-123";
        PortfolioModelV1 p1 = new PortfolioModelV1();
        p1.setName("P1");
        
        when(portfolioService.getPortfoliosByUserId(userId)).thenReturn(Arrays.asList(p1));

        mockMvc.perform(get("/v1/portfolios").param("userId", userId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].name").value("P1"));
    }

    @Test
    void getPortfolioBasicDetails_NoPortfolios_ReturnsNotFound() throws Exception {
        String userId = "empty-user";
        when(portfolioService.getPortfoliosByUserId(userId)).thenReturn(Collections.emptyList());

        mockMvc.perform(get("/v1/portfolios/list").param("userId", userId))
                .andExpect(status().isNotFound());
    }

    @Test
    void getPortfolioAnalysis_InvalidInterval_ReturnsBadRequest() throws Exception {
        mockMvc.perform(get("/v1/portfolios/{id}/analysis", UUID.randomUUID().toString())
                .param("userId", "u1")
                .param("interval", "invalid"))
                .andExpect(status().isBadRequest());
    }
}
