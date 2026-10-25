package com.portfolio.service.scheduler;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import com.portfolio.model.history.HistoryJobMode;
import com.portfolio.model.history.HistoryJobPhase;
import com.portfolio.model.history.HistoryJobState;
import com.portfolio.model.history.HistoryStatus;
import com.portfolio.redis.service.PortfolioHistoryJobRedisService;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;

@ExtendWith(MockitoExtension.class)
class PortfolioHistoryJobServiceTest {

    @Mock
    private PortfolioHistoryJobRedisService jobRedis;
    @Mock
    private SnapshotCatchUpService snapshotCatchUpService;

    private PortfolioHistoryJobService service;
    private SimpleMeterRegistry meters;

    @BeforeEach
    void setUp() {
        meters = new SimpleMeterRegistry();
        service = new PortfolioHistoryJobService(jobRedis, snapshotCatchUpService, meters);
        ReflectionTestUtils.setField(service, "debounceSeconds", 1);
    }

    @Test
    void enqueueIncrementsGenerationAndSchedules() {
        when(jobRedis.enqueue(eq("u1"), eq(HistoryJobMode.FULL), eq("p1")))
                .thenReturn(HistoryJobState.builder()
                        .userId("u1")
                        .generation(2)
                        .mode(HistoryJobMode.FULL)
                        .targetPortfolioId("p1")
                        .phase(HistoryJobPhase.QUEUED)
                        .historyStatus(HistoryStatus.BUILDING)
                        .build());

        HistoryJobState state = service.enqueue("u1", HistoryJobMode.FULL, "p1");
        assertEquals(2, state.getGeneration());
        assertEquals(HistoryJobMode.FULL, state.getMode());
    }

    @Test
    void runJobMarksReadyAndCountsSuccess() throws Exception {
        HistoryJobState queued = HistoryJobState.builder()
                .userId("u1")
                .generation(1)
                .mode(HistoryJobMode.MERGE_BROKER)
                .targetPortfolioId("p-b")
                .phase(HistoryJobPhase.QUEUED)
                .historyStatus(HistoryStatus.BUILDING)
                .build();
        when(jobRedis.tryLock("u1")).thenReturn(true);
        when(jobRedis.get("u1")).thenReturn(Optional.of(queued));
        when(snapshotCatchUpService.runHistoryBuild(eq("u1"), eq(HistoryJobMode.MERGE_BROKER), eq("p-b"), any()))
                .thenReturn(new SnapshotCatchUpService.CatchUpResult(
                        java.time.LocalDate.of(2026, 8, 1),
                        java.time.LocalDate.of(2026, 9, 20),
                        40));

        service.runJob("u1");

        ArgumentCaptor<HistoryJobState> cap = ArgumentCaptor.forClass(HistoryJobState.class);
        verify(jobRedis, org.mockito.Mockito.atLeastOnce()).save(cap.capture());
        HistoryJobState last = cap.getValue();
        assertEquals(HistoryJobPhase.READY, last.getPhase());
        assertEquals(HistoryStatus.READY, last.getHistoryStatus());
        assertEquals(40, last.getCoverageDays());
        assertTrue(meters.counter("portfolio.history.jobs", "mode", "MERGE_BROKER", "outcome", "success").count() >= 1);
        verify(jobRedis).unlock("u1");
    }

    @Test
    void runJobRerunsWhenGenerationDirty() {
        AtomicReference<Long> gen = new AtomicReference<>(1L);
        when(jobRedis.tryLock("u1")).thenReturn(true);
        when(jobRedis.get("u1")).thenAnswer(inv -> Optional.of(HistoryJobState.builder()
                .userId("u1")
                .generation(gen.get())
                .mode(HistoryJobMode.FULL)
                .phase(HistoryJobPhase.QUEUED)
                .historyStatus(HistoryStatus.BUILDING)
                .build()));
        when(snapshotCatchUpService.runHistoryBuild(eq("u1"), eq(HistoryJobMode.FULL), any(), any()))
                .thenAnswer(inv -> {
                    if (gen.get() == 1L) {
                        gen.set(2L);
                    }
                    return new SnapshotCatchUpService.CatchUpResult(null, null, 1);
                });

        service.runJob("u1");

        verify(snapshotCatchUpService, org.mockito.Mockito.times(2))
                .runHistoryBuild(eq("u1"), eq(HistoryJobMode.FULL), any(), any());
        verify(jobRedis, never()).unlock(eq("other"));
    }
}
