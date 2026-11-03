package com.portfolio.service.nps;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.am.common.amcommondata.document.nps.NpsImportDocument;
import com.am.common.amcommondata.model.nps.NpsDocumentMeta;
import com.am.common.amcommondata.repository.nps.NpsImportRepository;
import com.am.common.amcommondata.service.PortfolioService;
import com.portfolio.model.events.NpsDocumentEvent;

@ExtendWith(MockitoExtension.class)
class NpsImportServiceTest {

    @Mock
    private NpsImportRepository npsImportRepository;

    @Mock
    private PortfolioService portfolioService;

    @InjectMocks
    private NpsImportService npsImportService;

    @Test
    void upsertFromEvent_persistsPranAndTiers() {
        when(npsImportRepository.findFirstByOwnerOrderByAsOfDateDescImportedAtDesc("u1"))
                .thenReturn(Optional.empty());
        when(npsImportRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        NpsDocumentEvent event = NpsDocumentEvent.builder()
                .eventType("NPS_DOCUMENT")
                .userId("u1")
                .documentSource("NPS_STATEMENT")
                .asOfDate(LocalDate.of(2026, 9, 30))
                .statementTo(LocalDate.of(2026, 9, 30))
                .subscriber(Map.of("pran", "110115289881", "name", "Test User"))
                .tiers(List.of(
                        Map.of(
                                "tier", "I",
                                "investmentSummary", Map.of("holdingsValue", 100000.0))))
                .validation(NpsDocumentMeta.Validation.builder().allOk(true).build())
                .build();

        NpsImportDocument saved = npsImportService.upsertFromEvent(event);
        assertEquals("110115289881", saved.getPran());
        assertEquals("u1", saved.getOwner());

        ArgumentCaptor<NpsImportDocument> cap = ArgumentCaptor.forClass(NpsImportDocument.class);
        verify(npsImportRepository).save(cap.capture());
        assertNotNull(cap.getValue().getImportedAt());
        assertEquals("NPS_STATEMENT", cap.getValue().getDocumentSource());
    }

    @Test
    void latestMeta_includesNomineeAndRegistrationDate() {
        when(npsImportRepository.findFirstByOwnerOrderByAsOfDateDescImportedAtDesc("u1"))
                .thenReturn(Optional.of(NpsImportDocument.builder()
                        .owner("u1")
                        .pran("110115289881")
                        .asOfDate(LocalDate.of(2026, 9, 30))
                        .subscriber(Map.of(
                                "pran", "110115289881",
                                "name", "MUNISH",
                                "registrationDate", "21-Dec-20",
                                "nominee", Map.of("name", "ABHIMANYU KUMAR", "percentage", 100.0)))
                        .tiers(List.of(
                                Map.of(
                                        "tier", "I",
                                        "investmentSummary", Map.of("holdingsValue", 913551.5)),
                                Map.of(
                                        "tier", "II",
                                        "investmentSummary", Map.of("holdingsValue", 24356.16))))
                        .build()));

        NpsDocumentMeta meta = npsImportService.latestMeta("u1").orElseThrow();
        assertEquals("ABHIMANYU KUMAR (100%)", meta.getNomineeDetails());
        assertEquals("21-Dec-20", meta.getRegistrationDate());
        assertEquals(937907.66, meta.getTotalValue(), 0.01);
    }

    @Test
    void formatNominee_handlesMapAndBlank() {
        assertEquals(
                "ABHIMANYU KUMAR (100%)",
                NpsImportService.formatNominee(Map.of("name", "ABHIMANYU KUMAR", "percentage", 100.0)));
        assertEquals(null, NpsImportService.formatNominee(null));
        assertEquals(null, NpsImportService.formatNominee(Map.of()));
    }
}
