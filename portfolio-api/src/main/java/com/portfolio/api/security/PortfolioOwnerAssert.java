package com.portfolio.api.security;

import com.am.common.amcommondata.model.PortfolioModelV1;
import com.am.common.amcommondata.service.PortfolioService;
import com.am.security.context.UserContext;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

import java.util.UUID;

/**
 * Asserts the authenticated user owns the portfolio identified by {@code portfolioId}.
 * Reuse on all {@code /v1/analytics/portfolio/{portfolioId}/**} routes (advanced, intelligence, stress, what-if).
 */
@Component
@RequiredArgsConstructor
public class PortfolioOwnerAssert {

    private final PortfolioService portfolioService;

    /**
     * @return the loaded portfolio when the caller is the owner
     * @throws ResponseStatusException 400 invalid id, 404 missing, 403 not owner
     */
    public PortfolioModelV1 requireOwner(String portfolioId) {
        String userId = UserContext.getUserIdOrThrow();
        final UUID id;
        try {
            id = UUID.fromString(portfolioId);
        } catch (IllegalArgumentException ex) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid portfolioId");
        }

        PortfolioModelV1 portfolio = portfolioService.getPortfolioById(id);
        if (portfolio == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Portfolio not found");
        }
        if (!userId.equals(portfolio.getOwner())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Not owner of portfolio");
        }
        return portfolio;
    }
}
