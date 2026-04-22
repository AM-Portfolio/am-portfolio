package com.am.common.amcommondata.document.base;

import java.util.UUID;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Field;

import com.am.common.amcommondata.document.common.AuditMetadata;
import com.am.common.amcommondata.model.enums.DocumentStatus;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

@Data
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
public abstract class BaseDocument {
    
    @Id
    @Field("_id")
    private String id;
    
    @Field("baseStatus")
    private DocumentStatus status;
    
    @Field("audit")
    private AuditMetadata audit;
    
    @Field("parentId")
    private String parentId; // Reference to parent document if any, stored as String in MongoDB
}
