package com.portfolio.service.family;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.server.ResponseStatusException;

import com.am.common.amcommondata.document.family.FamilyMemberDocument;
import com.am.common.amcommondata.document.portfolio.PortfolioDocument;
import com.am.common.amcommondata.repository.cas.CasImportRepository;
import com.am.common.amcommondata.repository.family.FamilyMemberRepository;
import com.am.common.amcommondata.repository.nps.NpsImportRepository;
import com.am.common.amcommondata.repository.portfolio.PortfolioDocumentRepository;
import com.portfolio.service.portfolio.BrokerPortfolioDeleteService;

@ExtendWith(MockitoExtension.class)
class FamilyMemberDeleteServiceTest {

    @Mock
    private FamilyMemberRepository familyMemberRepository;
    @Mock
    private PortfolioDocumentRepository portfolioDocumentRepository;
    @Mock
    private CasImportRepository casImportRepository;
    @Mock
    private NpsImportRepository npsImportRepository;
    @Mock
    private BrokerPortfolioDeleteService brokerPortfolioDeleteService;

    @InjectMocks
    private FamilyMemberDeleteService familyMemberDeleteService;

    @Test
    void cascadeDeletesBooksCasNpsAndMember() {
        when(familyMemberRepository.findOwnedById("fm1", "u1")).thenReturn(Optional.of(FamilyMemberDocument.builder()
                .id("fm1")
                .ownerUserId("u1")
                .displayName("Kid")
                .build()));
        when(portfolioDocumentRepository.findByOwnerAndFamilyMemberId("u1", "fm1"))
                .thenReturn(List.of(
                        PortfolioDocument.builder().id("book-a").build(),
                        PortfolioDocument.builder().id("book-b").build()));
        when(casImportRepository.deleteByOwnerAndFamilyMemberId("u1", "fm1")).thenReturn(1L);
        when(npsImportRepository.deleteByOwnerAndFamilyMemberId("u1", "fm1")).thenReturn(0L);

        familyMemberDeleteService.deleteOwnedMember("fm1", "u1");

        verify(brokerPortfolioDeleteService).deleteOwnedPortfolioByDocumentId("book-a", "u1");
        verify(brokerPortfolioDeleteService).deleteOwnedPortfolioByDocumentId("book-b", "u1");
        verify(casImportRepository).deleteByOwnerAndFamilyMemberId("u1", "fm1");
        verify(npsImportRepository).deleteByOwnerAndFamilyMemberId("u1", "fm1");
        verify(familyMemberRepository).delete(org.mockito.ArgumentMatchers.any(FamilyMemberDocument.class));
    }

    @Test
    void foreignOwnerIsNotFound() {
        when(familyMemberRepository.findOwnedById("fm1", "u1")).thenReturn(Optional.empty());

        assertThrows(ResponseStatusException.class,
                () -> familyMemberDeleteService.deleteOwnedMember("fm1", "u1"));
        verify(brokerPortfolioDeleteService, never()).deleteOwnedPortfolioByDocumentId(anyString(), anyString());
        verify(familyMemberRepository, never()).delete(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void missingMemberIsNotFound() {
        when(familyMemberRepository.findOwnedById("missing", "u1")).thenReturn(Optional.empty());
        assertThrows(ResponseStatusException.class,
                () -> familyMemberDeleteService.deleteOwnedMember("missing", "u1"));
        verify(casImportRepository, never()).deleteByOwnerAndFamilyMemberId(eq("u1"), anyString());
    }
}
