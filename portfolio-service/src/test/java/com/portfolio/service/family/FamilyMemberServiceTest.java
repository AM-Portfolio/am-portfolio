package com.portfolio.service.family;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.server.ResponseStatusException;

import com.am.common.amcommondata.document.cas.CasImportDocument;
import com.am.common.amcommondata.document.family.FamilyMemberDocument;
import com.am.common.amcommondata.model.demat.DematAccountModel;
import com.am.common.amcommondata.model.family.FamilyMember;
import com.am.common.amcommondata.repository.cas.CasImportRepository;
import com.am.common.amcommondata.repository.family.FamilyMemberRepository;

@ExtendWith(MockitoExtension.class)
class FamilyMemberServiceTest {

    @Mock
    private FamilyMemberRepository familyMemberRepository;

    @Mock
    private CasImportRepository casImportRepository;

    @InjectMocks
    private FamilyMemberService familyMemberService;

    @Test
    void selfMatchReturnsNull() {
        assertNull(familyMemberService.resolveForImport(
                "u1", "SELF", "Me", "me@x.com", "CAS"));
    }

    @Test
    void familyCreatesMemberByEmail() {
        when(familyMemberRepository.consolidateObjectIdStringTwins("u1")).thenReturn(0);
        when(familyMemberRepository.findFirstByOwnerUserIdAndEmailNorm("u1", "spouse@x.com"))
                .thenReturn(Optional.empty());
        when(familyMemberRepository.countByOwnerUserId("u1")).thenReturn(0L);
        when(familyMemberRepository.savePreservingBsonId(any())).thenAnswer(inv -> {
            FamilyMemberDocument d = inv.getArgument(0);
            d.setId("fm1");
            return d;
        });

        FamilyMember m = familyMemberService.resolveForImport(
                "u1", "FAMILY", "Spouse", "spouse@x.com", "CAS");
        assertNotNull(m);
        assertEquals("fm1", m.getId());
        assertEquals("Spouse", m.getDisplayName());

        ArgumentCaptor<FamilyMemberDocument> cap = ArgumentCaptor.forClass(FamilyMemberDocument.class);
        verify(familyMemberRepository).savePreservingBsonId(cap.capture());
        assertEquals("spouse@x.com", cap.getValue().getEmailNorm());
    }

    @Test
    void familyReusesExistingByEmail() {
        when(familyMemberRepository.consolidateObjectIdStringTwins("u1")).thenReturn(0);
        when(familyMemberRepository.findFirstByOwnerUserIdAndEmailNorm("u1", "spouse@x.com"))
                .thenReturn(Optional.of(FamilyMemberDocument.builder()
                        .id("fm-existing")
                        .ownerUserId("u1")
                        .displayName("Spouse")
                        .email("spouse@x.com")
                        .emailNorm("spouse@x.com")
                        .build()));
        when(familyMemberRepository.savePreservingBsonId(any())).thenAnswer(inv -> inv.getArgument(0));

        FamilyMember m = familyMemberService.resolveForImport(
                "u1", "FAMILY", "Spouse", "spouse@x.com", "NPS");
        assertEquals("fm-existing", m.getId());
    }

    @Test
    void familyReplacesGenericDisplayNameWhenHolderNameArrives() {
        when(familyMemberRepository.consolidateObjectIdStringTwins("u1")).thenReturn(0);
        when(familyMemberRepository.findFirstByOwnerUserIdAndEmailNorm("u1", "priyanshukpd@gmail.com"))
                .thenReturn(Optional.of(FamilyMemberDocument.builder()
                        .id("fm1")
                        .ownerUserId("u1")
                        .displayName("Family member")
                        .email("priyanshukpd@gmail.com")
                        .emailNorm("priyanshukpd@gmail.com")
                        .build()));
        when(familyMemberRepository.savePreservingBsonId(any())).thenAnswer(inv -> inv.getArgument(0));

        FamilyMember m = familyMemberService.resolveForImport(
                "u1", "FAMILY", "PRIYANSHU KUMAR", "priyanshukpd@gmail.com", "CAS");
        assertEquals("PRIYANSHU KUMAR", m.getDisplayName());
        ArgumentCaptor<FamilyMemberDocument> cap = ArgumentCaptor.forClass(FamilyMemberDocument.class);
        verify(familyMemberRepository).savePreservingBsonId(cap.capture());
        assertEquals("priyanshu kumar", cap.getValue().getNameNorm());
    }

    @Test
    void createManualPersistsSourceManual() {
        when(familyMemberRepository.findFirstByOwnerUserIdAndEmailNorm("u1", "a@x.com"))
                .thenReturn(Optional.empty());
        when(familyMemberRepository.findFirstByOwnerUserIdAndNameNorm("u1", "alex"))
                .thenReturn(Optional.empty());
        when(familyMemberRepository.countByOwnerUserId("u1")).thenReturn(0L);
        when(familyMemberRepository.savePreservingBsonId(any())).thenAnswer(inv -> {
            FamilyMemberDocument d = inv.getArgument(0);
            d.setId("fm-new");
            return d;
        });

        FamilyMember m = familyMemberService.createManual("u1", "Alex", "a@x.com");
        assertEquals("fm-new", m.getId());
        assertEquals("MANUAL", m.getSource());
        assertEquals(0, m.getPortfolioCount());
    }

    @Test
    void createManualDuplicateEmailConflicts() {
        when(familyMemberRepository.findFirstByOwnerUserIdAndEmailNorm("u1", "a@x.com"))
                .thenReturn(Optional.of(FamilyMemberDocument.builder().id("other").build()));
        assertThrows(ResponseStatusException.class,
                () -> familyMemberService.createManual("u1", "Alex", "a@x.com"));
        verify(familyMemberRepository, never()).savePreservingBsonId(any());
    }

    @Test
    void createManualRejectsWhenAtMaxFive() {
        when(familyMemberRepository.findFirstByOwnerUserIdAndEmailNorm("u1", "a@x.com"))
                .thenReturn(Optional.empty());
        when(familyMemberRepository.findFirstByOwnerUserIdAndNameNorm("u1", "alex"))
                .thenReturn(Optional.empty());
        when(familyMemberRepository.countByOwnerUserId("u1")).thenReturn(5L);
        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> familyMemberService.createManual("u1", "Alex", "a@x.com"));
        assertEquals(409, ex.getStatusCode().value());
        verify(familyMemberRepository, never()).savePreservingBsonId(any());
    }

    @Test
    void resolveForImportRejectsNewWhenAtMaxFive() {
        when(familyMemberRepository.consolidateObjectIdStringTwins("u1")).thenReturn(0);
        when(familyMemberRepository.findFirstByOwnerUserIdAndEmailNorm("u1", "new@x.com"))
                .thenReturn(Optional.empty());
        when(familyMemberRepository.findFirstByOwnerUserIdAndNameNorm("u1", "new person"))
                .thenReturn(Optional.empty());
        when(familyMemberRepository.countByOwnerUserId("u1")).thenReturn(5L);
        assertThrows(ResponseStatusException.class,
                () -> familyMemberService.resolveForImport(
                        "u1", "FAMILY", "New Person", "new@x.com", "CAS"));
        verify(familyMemberRepository, never()).savePreservingBsonId(any());
    }

    @Test
    void getOwnedNotFound() {
        when(familyMemberRepository.consolidateObjectIdStringTwins("u1")).thenReturn(0);
        when(familyMemberRepository.findOwnedById("missing", "u1")).thenReturn(Optional.empty());
        assertThrows(ResponseStatusException.class,
                () -> familyMemberService.getOwned("missing", "u1"));
    }

    @Test
    void updateOwnedChangesNameAndCountsDemats() {
        when(familyMemberRepository.consolidateObjectIdStringTwins("u1")).thenReturn(0);
        when(familyMemberRepository.findOwnedById("fm1", "u1")).thenReturn(Optional.of(FamilyMemberDocument.builder()
                .id("fm1")
                .ownerUserId("u1")
                .displayName("Old")
                .nameNorm("old")
                .build()));
        when(familyMemberRepository.findFirstByOwnerUserIdAndNameNorm("u1", "new name"))
                .thenReturn(Optional.empty());
        when(familyMemberRepository.savePreservingBsonId(any())).thenAnswer(inv -> inv.getArgument(0));
        when(casImportRepository.findFirstByOwnerAndFamilyMemberIdOrderByAsOfDateDescImportedAtDesc("u1", "fm1"))
                .thenReturn(Optional.of(CasImportDocument.builder()
                        .accountDirectory(List.of(
                                DematAccountModel.builder().accountType("demat").accountNo("1").build(),
                                DematAccountModel.builder().accountType("demat").accountNo("2").build(),
                                DematAccountModel.builder().accountType("mf").accountNo("F1").build()))
                        .build()));

        FamilyMember m = familyMemberService.updateOwned("fm1", "u1", "New Name", null);
        assertEquals("New Name", m.getDisplayName());
        assertEquals(2, m.getPortfolioCount());
    }
}
