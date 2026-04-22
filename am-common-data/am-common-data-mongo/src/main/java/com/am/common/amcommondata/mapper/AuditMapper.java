package com.am.common.amcommondata.mapper;

import org.springframework.stereotype.Component;

import com.am.common.amcommondata.document.common.AuditMetadata;
import com.am.common.amcommondata.model.AuditModel;

@Component
public class AuditMapper {

    public AuditModel toModel(AuditMetadata metadata) {
        if (metadata == null) {
            return null;
        }

        return AuditModel.builder()
                .version(metadata.getVersion())
                .createdAt(metadata.getCreatedAt())
                .createdBy(metadata.getCreatedBy())
                .createdByEmail(metadata.getCreatedByEmail())
                .updatedAt(metadata.getUpdatedAt())
                .updatedBy(metadata.getUpdatedBy())
                .updatedByEmail(metadata.getUpdatedByEmail())
                .lastAction(metadata.getLastAction())
                .build();
    }

    public AuditMetadata toMetadata(AuditModel model) {
        if (model == null) {
            return null;
        }

        return AuditMetadata.builder()
                .version(model.getVersion())
                .createdAt(model.getCreatedAt())
                .createdBy(model.getCreatedBy())
                .createdByEmail(model.getCreatedByEmail())
                .updatedAt(model.getUpdatedAt())
                .updatedBy(model.getUpdatedBy())
                .updatedByEmail(model.getUpdatedByEmail())
                .lastAction(model.getLastAction())
                .build();
    }
}
