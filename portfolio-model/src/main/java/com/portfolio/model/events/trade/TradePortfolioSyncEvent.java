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
    private String id;
    private String brokerType;
    private String userId;
    private List<TradeEquityPosition> equities;
    private Object timestamp;
}
