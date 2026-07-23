package com.portfolio.model.events;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import lombok.Builder;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class StockPriceUpdateEvent {
    private String eventType;
    private String timestamp;
    private StockPriceData data;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class StockPriceData {
        private String symbol;
        
        @JsonAlias({"price", "lastPrice", "ltp"})
        private Double lastPrice;
        
        @JsonAlias({"previousClose", "prevClose"})
        private Double previousClose;
        
        @JsonAlias({"open", "openPrice"})
        private Double open;
        
        @JsonAlias({"dayHigh", "high", "highPrice"})
        private Double dayHigh;
        
        @JsonAlias({"dayLow", "low", "lowPrice"})
        private Double dayLow;
    }
}
