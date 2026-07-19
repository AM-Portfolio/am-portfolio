package com.am.common.amcommondata.document.marketcap;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "market_cap_cache")
@CompoundIndex(name = "symbol_idx", def = "{'symbol': 1}", unique = true)
public class MarketCapDocument {
    @Id
    private String symbol;
    private String sector;
    private String industry;
    private String marketCapType;
    private Double marketCapValue;
    private String companyName;
    private LocalDateTime updatedAt;
}
