package com.am.common.amcommondata.document.basket;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "basket_drafts")
public class BasketDraftDocument {

    @Id
    private String id;

    private String userId;

    private String sourcePortfolioId;

    private String etfIsin;

    private String etfName;

    private String basketName;

    private Double investmentAmount;

    private Double replicaScore;

    private Boolean hasCalculated;

    private List<String> excludedSymbols;

    /** symbol -> locked target qty */
    private Map<String, Integer> manualQtyOverrides;

    /** Full BasketOpportunity snapshot as JSON-compatible map. */
    private Map<String, Object> opportunity;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
