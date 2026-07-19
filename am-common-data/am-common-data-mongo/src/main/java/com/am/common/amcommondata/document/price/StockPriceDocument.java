package com.am.common.amcommondata.document.price;

import java.time.LocalDateTime;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.mapping.Document;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "stock_prices_cache")
@CompoundIndex(name = "symbol_idx", def = "{'symbol': 1}", unique = true)
public class StockPriceDocument {
    @Id
    private String symbol;
    private Double lastPrice;
    private Double previousClose;
    private Double openPrice;
    private Double highPrice;
    private Double lowPrice;
    private Long timestamp;
    private LocalDateTime updatedAt;
}
