package com.am.common.amcommondata.model;

import java.time.LocalDateTime;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AuditModel {
    private Long version;
    private LocalDateTime createdAt;
    private String createdBy;
    private String createdByEmail;
    private LocalDateTime updatedAt;
    private String updatedBy;
    private String updatedByEmail;
    private String lastAction;
}
