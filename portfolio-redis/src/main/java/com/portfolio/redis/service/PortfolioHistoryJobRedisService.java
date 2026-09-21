package com.portfolio.redis.service;

import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Service;

import com.portfolio.model.history.HistoryJobMode;
import com.portfolio.model.history.HistoryJobPhase;
import com.portfolio.model.history.HistoryJobState;
import com.portfolio.model.history.HistoryStatus;

import lombok.extern.slf4j.Slf4j;

/**
 * Per-user portfolio history job state + single-flight lock in Redis (fail-open).
 */
@Service
@Slf4j
public class PortfolioHistoryJobRedisService {

    private static final String JOB_PREFIX = "portfolio:history:job:";
    private static final String LOCK_PREFIX = "portfolio:history:lock:";

    @Value("${cache.redis.enabled:true}")
    private boolean redisEnabled;

    @Value("${app.history.job.ttl-hours:24}")
    private int jobTtlHours;

    @Value("${app.history.job.lock-ttl-minutes:30}")
    private int lockTtlMinutes;

    private final StringRedisTemplate stringRedisTemplate;

    public PortfolioHistoryJobRedisService(@Nullable StringRedisTemplate stringRedisTemplate) {
        this.stringRedisTemplate = stringRedisTemplate;
    }

    public Optional<HistoryJobState> get(String userId) {
        if (!usable() || userId == null || userId.isBlank()) {
            return Optional.empty();
        }
        try {
            Map<Object, Object> raw = stringRedisTemplate.opsForHash().entries(JOB_PREFIX + userId);
            if (raw == null || raw.isEmpty()) {
                return Optional.empty();
            }
            return Optional.of(fromHash(userId, raw));
        } catch (Exception e) {
            log.warn("[HistoryJob] Redis get failed userId={} — fail-open: {}", userId, e.getMessage());
            return Optional.empty();
        }
    }

    public HistoryJobState enqueue(String userId, HistoryJobMode mode, String targetPortfolioId) {
        HistoryJobState existing = get(userId).orElse(null);
        long generation = existing != null ? existing.getGeneration() + 1 : 1L;
        HistoryJobState state = HistoryJobState.builder()
                .userId(userId)
                .generation(generation)
                .mode(mode)
                .targetPortfolioId(targetPortfolioId)
                .phase(HistoryJobPhase.QUEUED)
                .historyStatus(HistoryStatus.BUILDING)
                .updatedAt(Instant.now())
                .coverageDays(existing != null ? existing.getCoverageDays() : 0)
                .historyFrom(existing != null ? existing.getHistoryFrom() : null)
                .historyTo(existing != null ? existing.getHistoryTo() : null)
                .build();
        save(state);
        return state;
    }

    public void save(HistoryJobState state) {
        if (!usable() || state == null || state.getUserId() == null) {
            return;
        }
        try {
            String key = JOB_PREFIX + state.getUserId();
            Map<String, String> map = toHash(state);
            stringRedisTemplate.opsForHash().putAll(key, map);
            stringRedisTemplate.expire(key, Duration.ofHours(Math.max(1, jobTtlHours)));
        } catch (Exception e) {
            log.warn("[HistoryJob] Redis save failed userId={} — fail-open: {}", state.getUserId(), e.getMessage());
        }
    }

    /** @return true if lock acquired */
    public boolean tryLock(String userId) {
        if (!usable() || userId == null) {
            return true; // fail-open: allow build without Redis
        }
        try {
            Boolean ok = stringRedisTemplate.opsForValue()
                    .setIfAbsent(LOCK_PREFIX + userId, "1", Duration.ofMinutes(Math.max(5, lockTtlMinutes)));
            return Boolean.TRUE.equals(ok);
        } catch (Exception e) {
            log.warn("[HistoryJob] Redis lock failed userId={} — fail-open: {}", userId, e.getMessage());
            return true;
        }
    }

    public void unlock(String userId) {
        if (!usable() || userId == null) {
            return;
        }
        try {
            stringRedisTemplate.delete(LOCK_PREFIX + userId);
        } catch (Exception e) {
            log.warn("[HistoryJob] Redis unlock failed userId={}: {}", userId, e.getMessage());
        }
    }

    private boolean usable() {
        return redisEnabled && stringRedisTemplate != null;
    }

    private static Map<String, String> toHash(HistoryJobState s) {
        Map<String, String> m = new HashMap<>();
        m.put("generation", String.valueOf(s.getGeneration()));
        m.put("mode", s.getMode() != null ? s.getMode().name() : HistoryJobMode.GAP.name());
        m.put("targetPortfolioId", s.getTargetPortfolioId() != null ? s.getTargetPortfolioId() : "");
        m.put("phase", s.getPhase() != null ? s.getPhase().name() : HistoryJobPhase.QUEUED.name());
        m.put("historyStatus", s.getHistoryStatus() != null ? s.getHistoryStatus().name() : HistoryStatus.BUILDING.name());
        m.put("startedAt", s.getStartedAt() != null ? s.getStartedAt().toString() : "");
        m.put("updatedAt", s.getUpdatedAt() != null ? s.getUpdatedAt().toString() : Instant.now().toString());
        m.put("historyFrom", s.getHistoryFrom() != null ? s.getHistoryFrom() : "");
        m.put("historyTo", s.getHistoryTo() != null ? s.getHistoryTo() : "");
        m.put("coverageDays", String.valueOf(s.getCoverageDays()));
        m.put("failureReason", s.getFailureReason() != null ? s.getFailureReason() : "");
        return m;
    }

    private static HistoryJobState fromHash(String userId, Map<Object, Object> raw) {
        return HistoryJobState.builder()
                .userId(userId)
                .generation(parseLong(raw.get("generation"), 0L))
                .mode(parseEnum(raw.get("mode"), HistoryJobMode.class, HistoryJobMode.GAP))
                .targetPortfolioId(emptyToNull(str(raw.get("targetPortfolioId"))))
                .phase(parseEnum(raw.get("phase"), HistoryJobPhase.class, HistoryJobPhase.QUEUED))
                .historyStatus(parseEnum(raw.get("historyStatus"), HistoryStatus.class, HistoryStatus.EMPTY))
                .startedAt(parseInstant(raw.get("startedAt")))
                .updatedAt(parseInstant(raw.get("updatedAt")))
                .historyFrom(emptyToNull(str(raw.get("historyFrom"))))
                .historyTo(emptyToNull(str(raw.get("historyTo"))))
                .coverageDays((int) parseLong(raw.get("coverageDays"), 0L))
                .failureReason(emptyToNull(str(raw.get("failureReason"))))
                .build();
    }

    private static String str(Object o) {
        return o == null ? "" : o.toString();
    }

    private static String emptyToNull(String s) {
        return s == null || s.isBlank() ? null : s;
    }

    private static long parseLong(Object o, long d) {
        try {
            return o == null || str(o).isBlank() ? d : Long.parseLong(str(o));
        } catch (NumberFormatException e) {
            return d;
        }
    }

    private static Instant parseInstant(Object o) {
        String s = str(o);
        if (s.isBlank()) {
            return null;
        }
        try {
            return Instant.parse(s);
        } catch (Exception e) {
            return null;
        }
    }

    private static <E extends Enum<E>> E parseEnum(Object o, Class<E> type, E def) {
        String s = str(o);
        if (s.isBlank()) {
            return def;
        }
        try {
            return Enum.valueOf(type, s);
        } catch (Exception e) {
            return def;
        }
    }
}
