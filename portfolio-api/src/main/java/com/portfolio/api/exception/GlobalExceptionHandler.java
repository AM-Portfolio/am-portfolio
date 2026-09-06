package com.portfolio.api.exception;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.context.request.WebRequest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import lombok.extern.slf4j.Slf4j;
import com.portfolio.basket.exception.EtfNotFoundException;
import com.portfolio.service.basket.exception.DraftLimitReachedException;

@ControllerAdvice
@ConditionalOnProperty(prefix = "am.api.core.exception-handler", name = "enabled", havingValue = "false", matchIfMissing = true)
@Slf4j
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

    @Override
    protected ResponseEntity<Object> handleExceptionInternal(
            Exception ex, Object body, HttpHeaders headers, HttpStatusCode statusCode, WebRequest request) {

        log.error("Handling exception [{}]: {}", statusCode, ex.getMessage(), ex);

        Map<String, Object> responseBody = new LinkedHashMap<>();
        responseBody.put("timestamp", LocalDateTime.now());
        responseBody.put("message", statusCode.value() >= 500
            ? "An unexpected error occurred. Please try again later."
            : ex.getMessage() != null ? ex.getMessage() : "Request could not be processed.");
        responseBody.put("status", statusCode.value());

        return new ResponseEntity<>(responseBody, headers, statusCode);
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Object> handleIllegalArgument(IllegalArgumentException ex, WebRequest request) {
        log.warn("Bad request: {}", ex.getMessage());

        Map<String, Object> responseBody = new LinkedHashMap<>();
        responseBody.put("timestamp", LocalDateTime.now());
        responseBody.put("message", ex.getMessage());
        responseBody.put("status", HttpStatus.BAD_REQUEST.value());

        return new ResponseEntity<>(responseBody, HttpStatus.BAD_REQUEST);
    }

    @ExceptionHandler(EtfNotFoundException.class)
    public ResponseEntity<Object> handleEtfNotFound(EtfNotFoundException ex, WebRequest request) {
        log.warn("ETF not found: {}", ex.getMessage());

        Map<String, Object> responseBody = new LinkedHashMap<>();
        responseBody.put("timestamp", LocalDateTime.now());
        responseBody.put("message", ex.getMessage());
        responseBody.put("errorCode", "ETF_NOT_FOUND");
        responseBody.put("status", HttpStatus.NOT_FOUND.value());

        return new ResponseEntity<>(responseBody, HttpStatus.NOT_FOUND);
    }

    @ExceptionHandler(DraftLimitReachedException.class)
    public ResponseEntity<Object> handleDraftLimitReached(DraftLimitReachedException ex, WebRequest request) {
        log.warn("Draft limit reached: {}", ex.getMessage());

        Map<String, Object> responseBody = new LinkedHashMap<>();
        responseBody.put("timestamp", LocalDateTime.now());
        responseBody.put("message", ex.getMessage());
        responseBody.put("errorCode", DraftLimitReachedException.ERROR_CODE);
        responseBody.put("status", HttpStatus.CONFLICT.value());

        return new ResponseEntity<>(responseBody, HttpStatus.CONFLICT);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Object> handleUncaughtException(Exception ex, WebRequest request) {
        log.error("Uncaught exception handled by GlobalExceptionHandler: {}", ex.getMessage(), ex);

        Map<String, Object> responseBody = new LinkedHashMap<>();
        responseBody.put("timestamp", LocalDateTime.now());
        responseBody.put("message", "An unexpected error occurred. Please try again later.");
        responseBody.put("type", ex.getClass().getSimpleName());
        responseBody.put("status", HttpStatus.INTERNAL_SERVER_ERROR.value());

        return new ResponseEntity<>(responseBody, HttpStatus.INTERNAL_SERVER_ERROR);
    }
}
