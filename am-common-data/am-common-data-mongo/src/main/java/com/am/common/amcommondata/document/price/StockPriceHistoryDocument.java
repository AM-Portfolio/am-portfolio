package com.am.common.amcommondata.document.price;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Date;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "stock_price_history")
@CompoundIndex(def = "{'symbol': 1, 'timestampMinute': 1}", unique = true)
public class StockPriceHistoryDocument {
    @Id
    private String id;
    
    @Indexed
    private String symbol;
    
    private Double price;
    
    private Long timestampMinute;
    
    private Date createdAt;
}
