package com.portfolio.marketdata.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * Request DTO for batch security search
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class BatchSearchRequest {

    private List<String> queries;
    @Builder.Default
    private Integer limit = 3;
    @Builder.Default
    private Double minMatchScore = 0.0;
}
