package com.portfolio.marketdata.client;

import java.time.Duration;
import java.time.LocalDate;

import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

import com.portfolio.marketdata.config.MarketDataApiConfig;
import com.portfolio.marketdata.model.calendar.MarketCalendarStatus;
import com.portfolio.marketdata.model.calendar.MarketSessionTiming;

import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Mono;

/**
 * Thin client for market-data calendar APIs. Uses a dedicated short timeout so holdings
 * warm path cannot stall on calendar.
 */
@Slf4j
@Component
public class MarketCalendarClient {

    private static final Duration DEFAULT_TIMEOUT = Duration.ofMillis(200);

    private final WebClient webClient;
    private final MarketDataApiConfig config;
    private final Duration timeout;

    public MarketCalendarClient(WebClient.Builder webClientBuilder, MarketDataApiConfig config) {
        this.config = config;
        this.timeout = Duration.ofMillis(Math.max(50, config.getCalendarTimeoutMs()));
        String calendarBase = (config.getCalendarBaseUrl() != null && !config.getCalendarBaseUrl().isBlank())
                ? config.getCalendarBaseUrl().trim()
                : config.getBaseUrl();
        this.webClient = WebClient.builder()
                .baseUrl(calendarBase)
                .build();
        log.info("[MarketCalendar] client baseUrl={}", calendarBase);
    }

    public Mono<MarketCalendarStatus> getStatus(String exchange) {
        String path = config.getStatusEndpoint();
        return webClient.get()
                .uri(uriBuilder -> uriBuilder.path(path).queryParam("exchange", exchange).build())
                .retrieve()
                .bodyToMono(MarketCalendarStatus.class)
                .timeout(timeout)
                .doOnError(e -> log.warn("[MarketCalendar] status failed exchange={}: {}", exchange, e.getMessage()));
    }

    public Mono<MarketSessionTiming> getTimings(String exchange, LocalDate date) {
        String path = config.getTimingsEndpoint();
        return webClient.get()
                .uri(uriBuilder -> uriBuilder
                        .path(path)
                        .queryParam("exchange", exchange)
                        .queryParam("date", date.toString())
                        .build())
                .retrieve()
                .bodyToMono(MarketSessionTiming.class)
                .timeout(timeout)
                .doOnError(e -> log.warn("[MarketCalendar] timings failed exchange={} date={}: {}",
                        exchange, date, e.getMessage()));
    }

    Duration timeout() {
        return timeout != null ? timeout : DEFAULT_TIMEOUT;
    }
}
