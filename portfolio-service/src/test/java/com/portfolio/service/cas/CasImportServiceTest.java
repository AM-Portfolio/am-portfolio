package com.portfolio.service.cas;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.am.common.amcommondata.document.cas.CasImportDocument;
import com.am.common.amcommondata.model.cas.CasDocumentMeta;
import com.am.common.amcommondata.model.demat.DematAccountModel;
import com.am.common.amcommondata.repository.cas.CasImportRepository;
import com.portfolio.model.events.CasDocumentEvent;
import com.portfolio.service.family.FamilyMemberService;

@ExtendWith(MockitoExtension.class)
class CasImportServiceTest {

    @Mock
    private CasImportRepository casImportRepository;

    @Mock
    private FamilyMemberService familyMemberService;

    @InjectMocks
    private CasImportService casImportService;

    @Test
    void upsertPersistsDirectoryAndMeta() {
        when(casImportRepository.findFirstByOwnerAndFamilyMemberIdIsNullOrderByAsOfDateDescImportedAtDesc("u1"))
                .thenReturn(Optional.empty());
        when(casImportRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(familyMemberService.resolveForImport(any(), any(), any(), any(), any(), any()))
                .thenReturn(null);

        CasDocumentEvent event = CasDocumentEvent.builder()
                .eventType("CAS_DOCUMENT")
                .userId("u1")
                .documentSource("CDSL_CAS")
                .asOfDate(LocalDate.of(2026, 9, 30))
                .cover(CasDocumentMeta.Cover.builder().totalValue(100.0).build())
                .validation(CasDocumentMeta.Validation.builder().allOk(true).build())
                .accountDirectory(List.of(
                        DematAccountModel.builder().accountNo("1208").accountType("demat").active(true).build()))
                .build();

        CasImportDocument saved = casImportService.upsertFromEvent(event);
        assertEquals("u1", saved.getOwner());
        assertEquals(LocalDate.of(2026, 9, 30), saved.getAsOfDate());
        assertEquals(1, saved.getAccountDirectory().size());
        assertTrue(Boolean.TRUE.equals(saved.getValidation().getAllOk()));

        ArgumentCaptor<CasImportDocument> cap = ArgumentCaptor.forClass(CasImportDocument.class);
        verify(casImportRepository).save(cap.capture());
        assertEquals("CDSL_CAS", cap.getValue().getDocumentSource());
    }

    @Test
    void upsertFamilyCasKeysByFamilyMemberId() {
        when(familyMemberService.resolveForImport(any(), any(), any(), any(), any(), any()))
                .thenReturn(com.am.common.amcommondata.model.family.FamilyMember.builder()
                        .id("fm1")
                        .displayName("Priyanshu Kumar")
                        .email("priyanshukpd@gmail.com")
                        .pan("JYQPK9320A")
                        .build());
        when(casImportRepository.findFirstByOwnerAndFamilyMemberIdOrderByAsOfDateDescImportedAtDesc("u1", "fm1"))
                .thenReturn(Optional.empty());
        when(casImportRepository.findFirstByOwnerAndFamilyMemberIdIsNullOrderByAsOfDateDescImportedAtDesc("u1"))
                .thenReturn(Optional.empty());
        when(casImportRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        CasDocumentEvent event = CasDocumentEvent.builder()
                .eventType("CAS_DOCUMENT")
                .userId("u1")
                .identityMatch("FAMILY")
                .holderName("Priyanshu Kumar")
                .holderEmail("priyanshukpd@gmail.com")
                .holderPan("JYQPK9320A")
                .documentSource("CDSL_CAS")
                .asOfDate(LocalDate.of(2026, 8, 31))
                .build();

        CasImportDocument saved = casImportService.upsertFromEvent(event);
        assertEquals("fm1", saved.getFamilyMemberId());
        assertEquals("Priyanshu Kumar", saved.getFamilyMemberName());
        assertEquals("JYQPK9320A", saved.getHolderPan());
        verify(casImportRepository)
                .findFirstByOwnerAndFamilyMemberIdOrderByAsOfDateDescImportedAtDesc("u1", "fm1");
    }

    @Test
    void upsertFamilyRetagsMatchingPrimaryCas() {
        when(familyMemberService.resolveForImport(any(), any(), any(), any(), any(), any()))
                .thenReturn(com.am.common.amcommondata.model.family.FamilyMember.builder()
                        .id("fm1")
                        .displayName("Priyanshu Kumar")
                        .email("priyanshukpd@gmail.com")
                        .pan("JYQPK9320A")
                        .build());
        when(casImportRepository.findFirstByOwnerAndFamilyMemberIdOrderByAsOfDateDescImportedAtDesc("u1", "fm1"))
                .thenReturn(Optional.empty());
        CasImportDocument primary = CasImportDocument.builder()
                .id("cas-primary")
                .owner("u1")
                .holderName("PRIYANSHU KUMAR")
                .holderEmail("priyanshukpd@gmail.com")
                .holderPan("JYQPK9320A")
                .build();
        when(casImportRepository.findFirstByOwnerAndFamilyMemberIdIsNullOrderByAsOfDateDescImportedAtDesc("u1"))
                .thenReturn(Optional.of(primary));
        when(casImportRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        CasDocumentEvent event = CasDocumentEvent.builder()
                .eventType("CAS_DOCUMENT")
                .userId("u1")
                .identityMatch("FAMILY")
                .holderName("Priyanshu Kumar")
                .holderEmail("priyanshukpd@gmail.com")
                .holderPan("JYQPK9320A")
                .documentSource("CDSL_CAS")
                .asOfDate(LocalDate.of(2026, 8, 31))
                .accountDirectory(List.of(
                        DematAccountModel.builder().accountNo("D1").accountType("demat").build()))
                .build();

        CasImportDocument saved = casImportService.upsertFromEvent(event);
        assertEquals("fm1", saved.getFamilyMemberId());
        assertEquals("cas-primary", saved.getId());
    }
}
