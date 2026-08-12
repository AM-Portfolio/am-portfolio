package com.am.common.amcommondata.listener;

import com.am.common.amcommondata.document.common.AuditMetadata;
import com.am.common.amcommondata.document.portfolio.PortfolioDocument;
import org.junit.jupiter.api.Test;
import org.springframework.data.mongodb.core.mapping.event.BeforeSaveEvent;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class VersionIncrementEventListenerTest {

    private final VersionIncrementEventListener listener = new VersionIncrementEventListener();

    @Test
    void nullVersionWithCreatedAt_defaultsToOne_noNpe() {
        PortfolioDocument doc = PortfolioDocument.builder()
                .id("basket-1")
                .name("Nifty IT · Zerodha")
                .build();
        doc.setAudit(AuditMetadata.builder()
                .createdAt(LocalDateTime.now())
                .createdBy("user")
                .version(null)
                .build());

        listener.onBeforeSave(new BeforeSaveEvent<>(doc, null, "portfolios"));

        assertNotNull(doc.getAudit().getVersion());
        assertEquals(1L, doc.getAudit().getVersion());
    }

    @Test
    void existingVersion_incrementsOnUpdate() {
        PortfolioDocument doc = PortfolioDocument.builder().id("p1").build();
        doc.setAudit(AuditMetadata.builder()
                .createdAt(LocalDateTime.now().minusDays(1))
                .version(3L)
                .build());

        listener.onBeforeSave(new BeforeSaveEvent<>(doc, null, "portfolios"));

        assertEquals(4L, doc.getAudit().getVersion());
    }
}
