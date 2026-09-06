package com.am.common.amcommondata.document.basket;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "basket_create_idempotency")
public class BasketCreateIdempotencyDocument {

    public static final String STATUS_IN_PROGRESS = "IN_PROGRESS";
    public static final String STATUS_COMPLETED = "COMPLETED";
    public static final String STATUS_FAILED = "FAILED";

    @Id
    private String idempotencyKey;

    @Indexed
    private String userId;

    private String portfolioId;

    private String responseJson;

    /** IN_PROGRESS | COMPLETED | FAILED */
    private String status;

    @Indexed(expireAfterSeconds = 86400) // runtime TTL via BasketCreateIdempotencyIndexConfig
    private LocalDateTime createdAt;
}
