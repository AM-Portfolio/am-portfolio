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

import lombok.extern.slf4j.Slf4j;

@ControllerAdvice
@Slf4j
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

    @Override
    protected ResponseEntity<Object> handleExceptionInternal(
            Exception ex, Object body, HttpHeaders headers, HttpStatusCode statusCode, WebRequest request) {
        
        log.error("Handling exception [{}]: {}", statusCode, ex.getMessage(), ex);

        Map<String, Object> responseBody = new LinkedHashMap<>();
        responseBody.put("timestamp", LocalDateTime.now());
        responseBody.put("message", ex.getMessage() != null ? ex.getMessage() : "An error occurred");
        responseBody.put("type", ex.getClass().getSimpleName());
        responseBody.put("status", statusCode.value());

        return new ResponseEntity<>(responseBody, headers, statusCode);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Object> handleUncaughtException(Exception ex, WebRequest request) {
        log.error("Uncaught exception handled by GlobalExceptionHandler: {}", ex.getMessage(), ex);
        
        Map<String, Object> responseBody = new LinkedHashMap<>();
        responseBody.put("timestamp", LocalDateTime.now());
        responseBody.put("message", "Internal Server Error: " + ex.getMessage());
        responseBody.put("type", ex.getClass().getSimpleName());
        responseBody.put("status", HttpStatus.INTERNAL_SERVER_ERROR.value());

        return new ResponseEntity<>(responseBody, HttpStatus.INTERNAL_SERVER_ERROR);
    }
}
