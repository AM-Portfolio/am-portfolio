package com.am.common.amcommondata.service;

import java.time.LocalDateTime;

import org.springframework.stereotype.Service;

import com.am.common.amcommondata.document.base.BaseDocument;
import com.am.common.amcommondata.document.common.AuditMetadata;


@Service
public class AuditService {
    
    public void updateAudit(BaseDocument document) {
        if (document.getAudit() == null) {
            document.setAudit(new AuditMetadata());
            document.getAudit().setCreatedAt(LocalDateTime.now());
            document.getAudit().setVersion(1L);
        } else {
            document.getAudit().setUpdatedAt(LocalDateTime.now());
            document.getAudit().setVersion(document.getAudit().getVersion() + 1);
        }
    }
}
