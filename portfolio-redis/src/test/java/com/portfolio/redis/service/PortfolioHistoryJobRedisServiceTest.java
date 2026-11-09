package com.portfolio.redis.service;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import com.portfolio.model.history.HistoryJobMode;
import com.portfolio.model.history.HistoryJobPhase;
import com.portfolio.model.history.HistoryJobState;
import com.portfolio.model.history.HistoryStatus;

class PortfolioHistoryJobRedisServiceTest {

    @Test
    void strongerModesAreRankedCorrectly() {
        assertTrue(PortfolioHistoryJobRedisService.isStronger(HistoryJobMode.FULL, HistoryJobMode.GAP));
        assertTrue(PortfolioHistoryJobRedisService.isStronger(HistoryJobMode.FULL, HistoryJobMode.MERGE_BROKER));
        assertTrue(PortfolioHistoryJobRedisService.isStronger(HistoryJobMode.MERGE_BROKER, HistoryJobMode.GAP));
        assertFalse(PortfolioHistoryJobRedisService.isStronger(HistoryJobMode.GAP, HistoryJobMode.FULL));
        assertFalse(PortfolioHistoryJobRedisService.isStronger(HistoryJobMode.GAP, HistoryJobMode.GAP));
    }

    @Test
    void pendingIncludesQueuedAndBuilding() {
        assertTrue(PortfolioHistoryJobRedisService.isPending(HistoryJobState.builder()
                .phase(HistoryJobPhase.QUEUED)
                .historyStatus(HistoryStatus.BUILDING)
                .build()));
        assertTrue(PortfolioHistoryJobRedisService.isPending(HistoryJobState.builder()
                .phase(HistoryJobPhase.BUILDING_90D)
                .historyStatus(HistoryStatus.BUILDING)
                .build()));
        assertFalse(PortfolioHistoryJobRedisService.isPending(HistoryJobState.builder()
                .phase(HistoryJobPhase.READY)
                .historyStatus(HistoryStatus.READY)
                .build()));
    }
}
