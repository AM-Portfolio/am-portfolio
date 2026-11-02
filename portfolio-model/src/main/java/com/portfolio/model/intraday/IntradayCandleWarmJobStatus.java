package com.portfolio.model.intraday;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Status for async admin warm of {@code stock_price_history} from historical-charts 1D.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class IntradayCandleWarmJobStatus {

    public enum Phase {
        QUEUED,
        RUNNING,
        DONE,
        FAILED
    }

    private String jobId;
    private Phase phase;
    private String universe;
    private int symbolTotal;
    private int symbolsSucceeded;
    private int symbolsFailed;
    private int symbolsSkipped;
    private long ticksWritten;
    private Instant startedAt;
    private Instant finishedAt;
    private String errorMessage;

    @Builder.Default
    private List<String> failedSymbols = new ArrayList<>();
}
