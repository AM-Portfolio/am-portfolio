package com.portfolio.app.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;

/**
 * Ensures every HTTP request has a traceId in the MDC, even for the
 * requests not sampled by Micrometer Tracing.
 *
 * <p>Priority: runs BEFORE Spring Security and all business filters.
 *
 * <p>Strategy:
 * <ol>
 *   <li>Use Micrometer's traceId if already set (sampled traffic)</li>
 *   <li>Extract from incoming W3C {@code traceparent} header</li>
 *   <li>Read {@code X-Correlation-Id} header</li>
 *   <li>Generate a fresh UUID</li>
 * </ol>
 */
@Slf4j
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
@ConditionalOnProperty(name = "am.observability.enabled", havingValue = "true", matchIfMissing = true)
public class TraceContextFilter extends OncePerRequestFilter {

    private static final String TRACEPARENT_HEADER = "traceparent";
    private static final String CORRELATION_HEADER = "X-Correlation-Id";

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();
        return path.startsWith("/actuator") ||
               path.startsWith("/swagger-ui") ||
               path.startsWith("/api-docs") ||
               path.startsWith("/v3/api-docs") ||
               path.equals("/favicon.ico");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        try {
            String traceId = resolveTraceId(request);
            String correlationId = resolveCorrelationId(request, traceId);

            MDC.put("traceId", traceId);
            MDC.put("correlationId", correlationId);
            MDC.put("request.method", request.getMethod());
            MDC.put("request.path", request.getRequestURI());

            // Propagate traceId back to caller for distributed tracing
            response.setHeader("X-Trace-Id", traceId);

            chain.doFilter(request, response);

            MDC.put("http.status", String.valueOf(response.getStatus()));
        } finally {
            MDC.remove("traceId");
            MDC.remove("spanId");
            MDC.remove("correlationId");
            MDC.remove("request.method");
            MDC.remove("request.path");
            MDC.remove("http.status");
        }
    }

    /**
     * Resolves the traceId from the best available source.
     */
    private String resolveTraceId(HttpServletRequest request) {
        // 1. Micrometer already populated MDC (sampled requests)
        String traceId = MDC.get("traceId");
        if (traceId != null && !traceId.isBlank()) return traceId;

        // 2. W3C traceparent: "00-{traceId}-{parentId}-{flags}"
        String traceparent = request.getHeader(TRACEPARENT_HEADER);
        if (traceparent != null) {
            String[] parts = traceparent.split("-");
            if (parts.length >= 2 && !parts[1].isBlank()) return parts[1];
        }

        // 3. X-Correlation-Id header from upstream
        String correlationId = request.getHeader(CORRELATION_HEADER);
        if (correlationId != null && !correlationId.isBlank()) return correlationId;

        // 4. Fallback: generate fresh UUID
        return UUID.randomUUID().toString().replace("-", "");
    }

    private String resolveCorrelationId(HttpServletRequest request, String traceId) {
        String correlationId = request.getHeader(CORRELATION_HEADER);
        return (correlationId != null && !correlationId.isBlank()) ? correlationId : traceId;
    }
}
