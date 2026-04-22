package com.am.common.amcommondata.document.security.metadata;

import org.springframework.data.mongodb.core.mapping.Field;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class KeyInfo {
    
    @Field("symbol")
    private String symbol;
    
    @Field("isin")
    private String isin;
}