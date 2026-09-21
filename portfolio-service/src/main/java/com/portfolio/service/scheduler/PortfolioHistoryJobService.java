package com.portfolio.service.scheduler;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.portfolio.model.history.HistoryJobMode;
import com.portfolio.model.history.HistoryJobPhase;
import com.portfolio.model.history.HistoryJobState;
import com.portfolio.model.history.HistoryStatus;
import com.portfolio.model.history.PortfolioHistoryStatusResponse;
import com.portfolio.redis.service.PortfolioHistoryJobRedisService;

import io.micrometer.core.instrument.MeterRegistry;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Per-user history job orchestration: debounce, single-flight, generation rerun.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PortfolioHistoryJobService {

    private final PortfolioHistoryJobRedisService jobRedis;
    private final SnapshotCatchUpService snapshotCatchUpService;
    private final MeterRegistry meterRegistry;

    @Value("${app.history.job.debounce-seconds:15}")
    private int debounceSeconds;

    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "portfolio-history-job");
        t.setDaemon(true);
        return t;
    });
    private final Map<String, ScheduledFuture<?>> pending = new ConcurrentHashMap<>();

    public HistoryJobState enqueue(String userId, HistoryJobMode mode, String targetPortfolioId) {
        if (userId == null || userId.isBlank()) {
            return null;
        }
        HistoryJobMode resolved = mode != null ? mode : HistoryJobMode.GAP;
        HistoryJobState state = jobRedis.enqueue(userId, resolved, targetPortfolioId);
        log.info("[HistoryJob] Enqueued userId={} mode={} targetPortfolioId={} generation={} debounceSec={}",
                userId, resolved, targetPortfolioId, state.getGeneration(), debounceSeconds);
        scheduleDebounced(userId);
        return state;
    }

    /** Dirty bump while a job is running / queued — same as re-enqueue with current mode. */
    public void bumpDirty(String userId) {
        if (userId == null || userId.isBlank()) {
            return;
        }
        HistoryJobState current = jobRedis.get(userId).orElse(null);
        HistoryJobMode mode = current != null && current.getMode() != null ? current.getMode() : HistoryJobMode.FULL;
        String target = current != null ? current.getTargetPortfolioId() : null;
        enqueue(userId, mode, target);
    }

    public PortfolioHistoryStatusResponse getStatus(String userId) {
        return jobRedis.get(userId)
                .map(this::toStatusResponse)
                .orElse(PortfolioHistoryStatusResponse.builder()
                        .historyStatus(HistoryStatus.EMPTY)
                        .phase(HistoryJobPhase.READY)
                        .coverageDays(0)
                        .build());
    }

    private void scheduleDebounced(String userId) {
        ScheduledFuture<?> prev = pending.remove(userId);
        if (prev != null) {
            prev.cancel(false);
        }
        ScheduledFuture<?> future = scheduler.schedule(
                () -> runJob(userId),
                Math.max(1, debounceSeconds),
                TimeUnit.SECONDS);
        pending.put(userId, future);
    }

    void runJob(String userId) {
        pending.remove(userId);
        if (!jobRedis.tryLock(userId)) {
            log.warn("[HistoryJob] Lock busy userId={} — will rely on dirty generation rerun", userId);
            return;
        }
        MDC.put("userId", userId);
        try {
            boolean rerun;
            do {
                rerun = false;
                HistoryJobState state = jobRedis.get(userId).orElse(null);
                if (state == null) {
                    return;
                }
                long genAtStart = state.getGeneration();
                state.setStartedAt(Instant.now());
                state.setUpdatedAt(Instant.now());
                state.setPhase(HistoryJobPhase.BUILDING_90D);
                state.setHistoryStatus(HistoryStatus.BUILDING);
                state.setFailureReason(null);
                jobRedis.save(state);

                MDC.put("generation", String.valueOf(genAtStart));
                MDC.put("mode", state.getMode() != null ? state.getMode().name() : "");
                if (state.getTargetPortfolioId() != null) {
                    MDC.put("portfolioId", state.getTargetPortfolioId());
                }

                log.info("[HistoryJob] Worker started userId={} mode={} target={} generation={}",
                        userId, state.getMode(), state.getTargetPortfolioId(), genAtStart);

                SnapshotCatchUpService.CatchUpResult result;
                try {
                    result = snapshotCatchUpService.runHistoryBuild(
                            userId,
                            state.getMode(),
                            state.getTargetPortfolioId(),
                            phase -> {
                                HistoryJobState s = jobRedis.get(userId).orElse(state);
                                s.setPhase(phase);
                                s.setUpdatedAt(Instant.now());
                                jobRedis.save(s);
                            });
                } catch (Exception e) {
                    log.error("[HistoryJob] FAILED userId={} generation={}", userId, genAtStart, e);
                    fail(userId, state, e.getMessage());
                    meterRegistry.counter("portfolio.history.jobs", "mode",
                            state.getMode() != null ? state.getMode().name() : "GAP",
                            "outcome", "failed").increment();
                    return;
                }

                HistoryJobState after = jobRedis.get(userId).orElse(state);
                if (after.getGeneration() != genAtStart) {
                    log.info("[HistoryJob] Dirty generation during run userId={} start={} now={} — rerun",
                            userId, genAtStart, after.getGeneration());
                    rerun = true;
                    continue;
                }

                after.setPhase(HistoryJobPhase.READY);
                after.setHistoryStatus(HistoryStatus.READY);
                after.setHistoryFrom(result.historyFrom() != null ? result.historyFrom().toString() : null);
                after.setHistoryTo(result.historyTo() != null ? result.historyTo().toString() : null);
                after.setCoverageDays(result.daysWritten());
                after.setUpdatedAt(Instant.now());
                after.setFailureReason(null);
                jobRedis.save(after);

                meterRegistry.counter("portfolio.history.jobs", "mode",
                        after.getMode() != null ? after.getMode().name() : "GAP",
                        "outcome", "success").increment();

                log.info("[HistoryJob] READY userId={} daysWritten={} from={} to={} elapsedMs={}",
                        userId, result.daysWritten(), after.getHistoryFrom(), after.getHistoryTo(),
                        after.getStartedAt() != null
                                ? Instant.now().toEpochMilli() - after.getStartedAt().toEpochMilli()
                                : -1);
            } while (rerun);
        } finally {
            jobRedis.unlock(userId);
            MDC.remove("userId");
            MDC.remove("generation");
            MDC.remove("mode");
            MDC.remove("portfolioId");
        }
    }

    private void fail(String userId, HistoryJobState state, String reason) {
        state.setPhase(HistoryJobPhase.FAILED);
        state.setHistoryStatus(HistoryStatus.EMPTY);
        state.setFailureReason(reason != null ? reason : "unknown");
        state.setUpdatedAt(Instant.now());
        jobRedis.save(state);
    }

    private PortfolioHistoryStatusResponse toStatusResponse(HistoryJobState s) {
        return PortfolioHistoryStatusResponse.builder()
                .historyStatus(s.getHistoryStatus())
                .phase(s.getPhase())
                .mode(s.getMode())
                .targetPortfolioId(s.getTargetPortfolioId())
                .startedAt(s.getStartedAt() != null ? s.getStartedAt().toString() : null)
                .historyFrom(s.getHistoryFrom())
                .historyTo(s.getHistoryTo())
                .coverageDays(s.getCoverageDays())
                .failureReason(s.getFailureReason())
                .build();
    }

    @PreDestroy
    void shutdown() {
        scheduler.shutdownNow();
    }
}
