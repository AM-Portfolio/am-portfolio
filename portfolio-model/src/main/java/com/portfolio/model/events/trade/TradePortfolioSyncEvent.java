package com.portfolio.model.events.trade;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class TradePortfolioSyncEvent {
    /** Globally unique ID set by the producer. Used for stable idempotent deduplication. */
    private String eventId;

    /** The service that produced this event, e.g. "am-trade-management". */
    private String source;

    /** Payload schema version, e.g. "1.0". Allows consumers to handle future schema changes. */
    private String dataVersion;

    private String id;
    private String portfolioId;
    private String brokerType;
    private String userId;
    private String action;
    private Boolean deleteAllTrades;
    private List<TradeEquityPosition> equities;
    private Object timestamp;
}
