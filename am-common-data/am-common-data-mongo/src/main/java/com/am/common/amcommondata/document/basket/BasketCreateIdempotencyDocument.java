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

    @Id
    private String idempotencyKey;

    @Indexed
    private String userId;

    private String portfolioId;

    private String responseJson;

    @Indexed(expireAfter = "24h")
    private LocalDateTime createdAt;
}
