package com.portfolio.kafka.api;

import com.am.common.amcommondata.model.PortfolioModelV1;
import com.am.common.amcommondata.model.enums.PortfolioKind;
import com.am.common.amcommondata.service.PortfolioService;
import com.portfolio.kafka.publisher.PortfolioEventPublisher;
import io.swagger.v3.oas.annotations.Hidden;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * DEV/ADMIN repair: re-publish am-portfolio Mongo rows onto {@code am-portfolio-update}
 * so am-trade-management can upsert portfolios that missed Kafka fan-out
 * (e.g. consumer on {@code auto-offset-reset: latest} during a deploy window).
 */
@Hidden
@RestController
@RequestMapping("/v1/portfolios/dev")
@RequiredArgsConstructor
@Slf4j
public class PortfolioTradeFanoutController {

    private final PortfolioService portfolioService;
    private final PortfolioEventPublisher portfolioEventPublisher;

    @Value("${app.jwt.internal-secret}")
    private String internalSecret;

    /**
     * Usage: POST /v1/portfolios/dev/republish-to-trade?userId=&lt;uuid&gt;
     * Header: X-Internal-Secret: &lt;INTERNAL_SECRET_KEY&gt;
     */
    @PostMapping("/republish-to-trade")
    public ResponseEntity<Map<String, Object>> republishToTrade(
            @RequestParam(required = false) String userId,
            @RequestHeader(value = "X-Internal-Secret", required = false) String secret) {
        if (secret == null || !internalSecret.equals(secret)) {
            return ResponseEntity.status(403).body(Map.of("error", "Forbidden"));
        }

        List<PortfolioModelV1> portfolios = new ArrayList<>();
        if (userId != null && !userId.isBlank()) {
            portfolios.addAll(portfolioService.getPortfoliosByUserId(userId));
        } else {
            for (String uid : portfolioService.getAllUserIds()) {
                portfolios.addAll(portfolioService.getPortfoliosByUserId(uid));
            }
        }

        int published = 0;
        int skipped = 0;
        List<String> portfolioIds = new ArrayList<>();
        for (PortfolioModelV1 portfolio : portfolios) {
            if (portfolio == null || portfolio.getOwner() == null || portfolio.getId() == null) {
                skipped++;
                continue;
            }
            // Never fan out baskets — trade-management only needs BROKER holdings.
            if (!PortfolioKind.isBroker(portfolio.getPortfolioKind())) {
                skipped++;
                continue;
            }
            portfolioEventPublisher.publishPortfolioUpdate(portfolio, "PORTFOLIO_REPUBLISH");
            published++;
            portfolioIds.add(portfolio.getId().toString());
        }

        log.info("[DEV] republish-to-trade complete: userId={} published={} skipped={}",
                userId, published, skipped);

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("userId", userId);
        body.put("published", published);
        body.put("skipped", skipped);
        body.put("portfolioIds", portfolioIds);
        return ResponseEntity.ok(body);
    }
}
