package com.portfolio.redis.config;

import com.am.libraries.featureflag.service.GrowthBookService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import java.lang.reflect.Proxy;
import java.util.Collections;
import java.util.List;

@Slf4j
public class FeatureFlaggedRedisTemplate<K, V> extends RedisTemplate<K, V> {

    private final GrowthBookService growthBookService;
    private final String flagKey = "redis-enabled";
    private volatile Boolean lastState = null;
    private ValueOperations<K, V> valueOpsProxy;

    public FeatureFlaggedRedisTemplate(GrowthBookService growthBookService) {
        this.growthBookService = growthBookService;
    }

    private boolean isRedisEnabled() {
        boolean enabled = growthBookService.isOn(flagKey);
        if (lastState == null || lastState != enabled) {
            lastState = enabled;
            if (enabled) {
                log.info("[Redis-FF] Redis caching is now ENABLED via GrowthBook feature flag.");
            } else {
                log.warn("[Redis-FF] Redis caching is now DISABLED via GrowthBook feature flag. Bypassing all cache operations.");
            }
        }
        return enabled;
    }

    @Override
    @SuppressWarnings("unchecked")
    public ValueOperations<K, V> opsForValue() {
        if (valueOpsProxy == null) {
            ValueOperations<K, V> realOps = super.opsForValue();
            valueOpsProxy = (ValueOperations<K, V>) Proxy.newProxyInstance(
                    ValueOperations.class.getClassLoader(),
                    new Class<?>[]{ValueOperations.class},
                    (proxy, method, args) -> {
                        if (!isRedisEnabled()) {
                            return handleDisabledOps(method.getName(), args, method.getReturnType());
                        }
                        return method.invoke(realOps, args);
                    }
            );
        }
        return valueOpsProxy;
    }

    private Object handleDisabledOps(String methodName, Object[] args, Class<?> returnType) {
        if (List.class.isAssignableFrom(returnType)) {
            if ("multiGet".equals(methodName) && args != null && args.length > 0 && args[0] instanceof java.util.Collection) {
                return Collections.nCopies(((java.util.Collection<?>) args[0]).size(), null);
            }
            return Collections.emptyList();
        }
        if (returnType == Boolean.class || returnType == boolean.class) {
            if ("setIfAbsent".equals(methodName)) return true;
            return false;
        }
        return null;
    }
}
