package com.portfolio.marketdata.client.base;

import java.time.Duration;
import java.util.function.Function;

import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Mono;
import reactor.util.retry.Retry;

/**
 * Abstract implementation of ApiClient that provides common functionality.
 */
@Slf4j
public abstract class AbstractApiClient implements ApiClient {

    protected final WebClient webClient;
    
    /**
     * Creates a new AbstractApiClient with the specified WebClient.
     * 
     * @param webClient the WebClient to use
     */
    protected AbstractApiClient(WebClient webClient) {
        this.webClient = webClient;
    }
    
    /**
     * Creates a new AbstractApiClient with a WebClient built from the specified base URL.
     * 
     * @param baseUrl the base URL for the API
     */
    protected AbstractApiClient(String baseUrl) {
        this.webClient = WebClient.builder()
                .baseUrl(baseUrl)
                .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .defaultHeader(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE)
                .defaultHeader(HttpHeaders.USER_AGENT, "Mozilla/5.0")
                .build();
    }

    @Override
    public <T> Mono<T> get(String path, Class<T> responseType) {
        return executeWithRetry(client -> client
                .get()
                .uri(path)
                .retrieve()
                .bodyToMono(responseType));
    }

    @Override
    public <T> Mono<T> get(String path, Class<T> responseType, Object... queryParams) {
        return executeWithRetry(client -> client
                .get()
                .uri(uriBuilder -> {
                    if (queryParams.length % 2 != 0) {
                        throw new IllegalArgumentException("Query parameters must be provided as key-value pairs");
                    }
                    
                    for (int i = 0; i < queryParams.length; i += 2) {
                        uriBuilder = uriBuilder.queryParam(queryParams[i].toString(), queryParams[i + 1]);
                    }
                    
                    return uriBuilder.path(path).build();
                })
                .retrieve()
                .bodyToMono(responseType));
    }
    
    /**
     * Executes a WebClient request with retry logic.
     * 
     * @param <T> the response type
     * @param requestFunction the function that executes the request
     * @return a Mono of the response type
     */
    protected <T> Mono<T> executeWithRetry(Function<WebClient, Mono<T>> requestFunction) {
        return requestFunction.apply(webClient)
                .timeout(Duration.ofMillis(getReadTimeout()))
                .doOnError(e -> log.error("API request failed: {}", e.getMessage()))
                .retryWhen(Retry.backoff(getMaxRetryAttempts(), Duration.ofSeconds(1))
                        .filter(e -> !(e instanceof WebClientResponseException.NotFound))
                        .onRetryExhaustedThrow((retryBackoffSpec, retrySignal) -> {
                            log.error("Retry attempts exhausted: {}", retrySignal.failure().getMessage());
                            return retrySignal.failure();
                        }));
    }
}
