package com.portfolio.aspect;

import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import com.portfolio.model.PortfolioAnalysis;

import lombok.extern.slf4j.Slf4j;

import java.time.LocalDateTime;
import java.time.ZoneId;

@Aspect
@Component
@Order(1)
@Slf4j
public class CacheLoggingAspect {

    @Around("@annotation(cacheable)")
    public Object logCacheAccess(ProceedingJoinPoint joinPoint, Cacheable cacheable) throws Throwable {
        long startTime = System.currentTimeMillis();
        Object result = joinPoint.proceed();
        long endTime = System.currentTimeMillis();

        if (result instanceof PortfolioAnalysis analysis) {
            String portfolioId = analysis.getPortfolioId();
            String userId = analysis.getUserId();
            
            LocalDateTime lastUpdated = LocalDateTime.ofInstant(
                analysis.getLastUpdated(), 
                ZoneId.systemDefault()
            );

            // If processing time is very short (< 10ms), it's likely from cache
            boolean isFromCache = (endTime - startTime) < 10;
            
            log.info("{} - Portfolio: {}, User: {}, Last Updated: {}, Processing Time: {}ms, " +
                    "Top Gainer: {}, Top Loser: {}", 
                isFromCache ? "Serving from cache" : "Fresh calculation",
                portfolioId,
                userId,
                lastUpdated,
                endTime - startTime,
                !analysis.getTopFiveGainers().isEmpty() ? 
                    String.format("%s (%.2f%%)", 
                        analysis.getTopFiveGainers().get(0).getSymbol(), 
                        analysis.getTopFiveGainers().get(0).getGainLossPercentage()) : "None",
                !analysis.getTopFiveLosers().isEmpty() ? 
                    String.format("%s (%.2f%%)", 
                        analysis.getTopFiveLosers().get(0).getSymbol(), 
                        analysis.getTopFiveLosers().get(0).getGainLossPercentage()) : "None"
            );
        }
        
        return result;
    }
}
