package com.portfolio.redis.service;

import java.util.Collections;
import org.springframework.beans.factory.annotation.Value;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.Set;

@Slf4j
@Service
@RequiredArgsConstructor
public class UserSessionRedisService {

    
    @org.springframework.beans.factory.annotation.Value("${cache.redis.enabled:true}")
    private boolean isRedisEnabled;
private final StringRedisTemplate redisTemplate;
    private static final String ACTIVE_USERS_KEY = "portfolio:active_users";

    public void addActiveUser(String userId) {
        if (!isRedisEnabled) return;
        redisTemplate.opsForSet().add(ACTIVE_USERS_KEY, userId);
    }

    public void removeActiveUser(String userId) {
        if (!isRedisEnabled) return;
        redisTemplate.opsForSet().remove(ACTIVE_USERS_KEY, userId);
    }

    public boolean isUserActive(String userId) {
        return Boolean.TRUE.equals(redisTemplate.opsForSet().isMember(ACTIVE_USERS_KEY, userId));
    }

    public Set<String> getActiveUsers() {
        return redisTemplate.opsForSet().members(ACTIVE_USERS_KEY);
    }
}

