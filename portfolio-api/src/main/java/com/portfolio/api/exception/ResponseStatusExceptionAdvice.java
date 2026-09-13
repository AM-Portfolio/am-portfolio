package com.portfolio.api.exception;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.server.ResponseStatusException;

import lombok.extern.slf4j.Slf4j;

/**
 * Always-on mapping for {@link ResponseStatusException} so am-api-core's catch-all
 * does not promote 4xx (403/400/404) to HTTP 500.
 */
@ControllerAdvice
@Order(Ordered.HIGHEST_PRECEDENCE)
@Slf4j
public class ResponseStatusExceptionAdvice {

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<Object> handle(ResponseStatusException ex) {
        HttpStatusCode status = ex.getStatusCode();
        log.warn("ResponseStatusException {}: {}", status.value(), ex.getReason());

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("timestamp", LocalDateTime.now());
        body.put("message", ex.getReason() != null ? ex.getReason() : status.toString());
        body.put("status", status.value());
        return new ResponseEntity<>(body, status);
    }
}
