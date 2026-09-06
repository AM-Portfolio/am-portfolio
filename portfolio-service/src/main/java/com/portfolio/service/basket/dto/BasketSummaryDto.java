package com.portfolio.service.basket.dto;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
public class BasketSummaryDto {
    private String id;
    private String etfName;
    private String etfIsin;
    private String status;
    private Integer assetCount;
    private Integer gapMissingCount;
    private Double totalValue;
    private Double investmentAmount;
    private LocalDateTime createdAt;
}
