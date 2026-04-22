package com.am.common.amcommondata.listener;

import java.time.LocalDateTime;

import org.springframework.data.mongodb.core.mapping.event.AbstractMongoEventListener;
import org.springframework.data.mongodb.core.mapping.event.BeforeSaveEvent;
import org.springframework.stereotype.Component;

import com.am.common.amcommondata.document.base.BaseDocument;
import com.am.common.amcommondata.document.common.AuditMetadata;

@Component
public class VersionIncrementEventListener extends AbstractMongoEventListener<BaseDocument> {

    @Override
    public void onBeforeSave(BeforeSaveEvent<BaseDocument> event) {
        BaseDocument document = event.getSource();
        if (document != null) {
            AuditMetadata audit = document.getAudit();
            if (audit == null) {
                audit = new AuditMetadata();
                audit.setVersion(1L);
                audit.setCreatedAt(LocalDateTime.now());
                audit.setLastAction("CREATE");
                document.setAudit(audit);
            } else {
                // Only increment version if this is an update
                if (audit.getCreatedAt() != null) {
                    audit.setVersion(audit.getVersion() + 1);
                    audit.setUpdatedAt(LocalDateTime.now());
                    if (audit.getLastAction() == null) {
                        audit.setLastAction("UPDATE");
                    }
                }
            }
        }
    }
}
