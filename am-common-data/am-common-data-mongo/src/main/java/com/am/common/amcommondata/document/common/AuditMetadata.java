package com.am.common.amcommondata.document.common;

import java.time.LocalDateTime;

import org.springframework.data.mongodb.core.mapping.Field;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(Include.NON_NULL)

public class AuditMetadata {
    @Field("created_at")
    private LocalDateTime createdAt;
    
    @Field("created_by")
    private String createdBy;
    
    @Field("created_by_email")
    private String createdByEmail;
    
    @Field("updated_at")
    private LocalDateTime updatedAt;
    
    @Field("updated_by")
    private String updatedBy;
    
    @Field("updated_by_email")
    private String updatedByEmail;
    
    @Field("version")
    @Builder.Default
    private Long version = 1L;
    
    @Field("last_action")
    private String lastAction; // CREATE, UPDATE, VERIFY, APPROVE, etc.
    
    @Field("last_action_comment")
    private String lastActionComment;
    
    @Field("source_system")
    private String sourceSystem; // System that created/updated this record
    
    @Field("source_reference")
    private String sourceReference; // Reference ID in the source system
}
