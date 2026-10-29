package com.am.common.amcommondata.document.stress;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "index_sleeves")
public class IndexSleeveDocument {

    @Id
    private String id;

    @Indexed(unique = true)
    @Field("symbol")
    private String symbol;

    @Field("sleeve_type")
    private String sleeveType; // "RAW_INDEX" or "BROAD_NIFTY_ETF"

    @Field("description")
    private String description;

    @Field("is_active")
    @Builder.Default
    private boolean active = true;

    @Field("created_at")
    private LocalDateTime createdAt;

    @Field("updated_at")
    private LocalDateTime updatedAt;
}
