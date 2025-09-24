package com.portfolio.marketdata.model.indices;

import java.time.LocalDateTime;

import lombok.Data;

/**
 * Model class representing audit information for index data.
 */
@Data
public class AuditInfo {
    private LocalDateTime updatedAt;
    private LocalDateTime createdAt;
}
