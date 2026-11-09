package com.portfolio.service.basket.dto;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
public class BasketDetailDto {
    private String id;
    private String name;
    private String etfName;
    private String etfIsin;
    private String status;
    private Double totalInvestedValue;
    private Double totalCurrentValue;
    private Double investmentAmount;
    private Double totalPnL;
    private Double pnlPercent;
    private Double coveragePercent;
    private Double replicaScore;
    private Double coverageAfterCreation;
    private Integer totalItems;
    private Integer heldCount;
    private Integer missingCount;
    private Integer underfundedCount;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private List<BasketLineDetailDto> lines;
}
