package com.portfolio.api;

import java.util.Map;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.portfolio.model.intraday.IntradayCandleWarmJobStatus;
import com.portfolio.service.intraday.IntradayCandleWarmService;

import io.swagger.v3.oas.annotations.Hidden;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Ops/admin: warm {@code stock_price_history} from am-market historical-charts (1D session).
 * Protected like other {@code /dev/} endpoints via {@code X-Internal-Secret}.
 */
@Hidden
@RestController
@RequestMapping("/v1/portfolios/admin/intraday-candles")
@RequiredArgsConstructor
@Slf4j
public class AdminIntradayCandleController {

    private final IntradayCandleWarmService intradayCandleWarmService;

    @Value("${app.jwt.internal-secret}")
    private String internalSecret;

    /**
     * POST /v1/portfolios/admin/intraday-candles/warm?universe=NIFTY_500
     * Optional: symbols=RELIANCE,TCS (overrides universe for dig smoke).
     */
    @PostMapping("/warm")
    public ResponseEntity<?> startWarm(
            @RequestParam(required = false, defaultValue = "NIFTY_500") String universe,
            @RequestParam(required = false) String symbols,
            @RequestHeader(value = "X-Internal-Secret", required = false) String secret) {
        if (secret == null || !internalSecret.equals(secret)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("error", "Forbidden"));
        }
        try {
            IntradayCandleWarmJobStatus status = intradayCandleWarmService.startWarm(universe, symbols);
            log.info("[Admin] Intraday candle warm accepted jobId={} universe={} symbols={}",
                    status.getJobId(), status.getUniverse(), status.getSymbolTotal());
            return ResponseEntity.status(HttpStatus.ACCEPTED).body(status);
        } catch (IllegalArgumentException | IllegalStateException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            log.error("[Admin] Intraday candle warm failed to start: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", e.getMessage() != null ? e.getMessage() : "start failed"));
        }
    }

    /**
     * GET /v1/portfolios/admin/intraday-candles/warm/{jobId}
     */
    @GetMapping("/warm/{jobId}")
    public ResponseEntity<?> getWarmStatus(
            @PathVariable String jobId,
            @RequestHeader(value = "X-Internal-Secret", required = false) String secret) {
        if (secret == null || !internalSecret.equals(secret)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("error", "Forbidden"));
        }
        IntradayCandleWarmJobStatus status = intradayCandleWarmService.getStatus(jobId);
        if (status == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", "job not found"));
        }
        return ResponseEntity.ok(status);
    }
}
