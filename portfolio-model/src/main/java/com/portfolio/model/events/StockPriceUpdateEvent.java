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
    private Object timestamp;
    
    @JsonAlias({"data", "equityPrices"})
    @com.fasterxml.jackson.annotation.JsonFormat(with = com.fasterxml.jackson.annotation.JsonFormat.Feature.ACCEPT_SINGLE_VALUE_AS_ARRAY)
    private java.util.List<StockPriceData> data;

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

        @com.fasterxml.jackson.annotation.JsonProperty("ohlcv")
        private void unpackNested(java.util.Map<String, Object> ohlcv) {
            if (ohlcv != null) {
                Object openVal = ohlcv.get("open");
                if (openVal instanceof Number) {
                    this.open = ((Number) openVal).doubleValue();
                }
                Object highVal = ohlcv.get("high");
                if (highVal instanceof Number) {
                    this.dayHigh = ((Number) highVal).doubleValue();
                }
                Object lowVal = ohlcv.get("low");
                if (lowVal instanceof Number) {
                    this.dayLow = ((Number) lowVal).doubleValue();
                }
            }
        }
    }
}
