package com.portfolio.redis.exception;

/**
 * Custom exception for Redis operation failures
 */
public class RedisOperationException extends RuntimeException {
    
    public RedisOperationException(String message) {
        super(message);
    }

    public RedisOperationException(String message, Throwable cause) {
        super(message, cause);
    }
}
