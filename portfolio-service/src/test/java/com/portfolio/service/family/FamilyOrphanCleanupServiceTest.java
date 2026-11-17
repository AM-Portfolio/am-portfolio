package com.portfolio.service.family;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.am.common.amcommondata.document.portfolio.PortfolioDocument;
import com.am.common.amcommondata.model.family.FamilyMember;
import com.am.common.amcommondata.repository.cas.CasImportRepository;
import com.am.common.amcommondata.repository.nps.NpsImportRepository;
import com.am.common.amcommondata.repository.portfolio.PortfolioDocumentRepository;
import com.portfolio.service.portfolio.BrokerPortfolioDeleteService;

@ExtendWith(MockitoExtension.class)
class FamilyOrphanCleanupServiceTest {

    @Mock
    private FamilyMemberService familyMemberService;
    @Mock
    private PortfolioDocumentRepository portfolioDocumentRepository;
    @Mock
    private CasImportRepository casImportRepository;
    @Mock
    private NpsImportRepository npsImportRepository;
    @Mock
    private BrokerPortfolioDeleteService brokerPortfolioDeleteService;

    @InjectMocks
    private FamilyOrphanCleanupService familyOrphanCleanupService;

    @Test
    void dryRunListsOrphansOnly() {
        when(familyMemberService.listForOwner("u1")).thenReturn(List.of());
        when(portfolioDocumentRepository.findByOwner("u1")).thenReturn(List.of(
                PortfolioDocument.builder()
                        .id("orphan")
                        .familyMemberId("gone")
                        .familyMemberName("PRIYANSHU KUMAR")
                        .totalValue(100.0)
                        .build(),
                PortfolioDocument.builder().id("primary").familyMemberId(null).totalValue(50.0).build()));
        when(casImportRepository.findByOwnerOrderByAsOfDateDescImportedAtDesc("u1")).thenReturn(List.of());
        when(npsImportRepository.findByOwnerOrderByAsOfDateDescImportedAtDesc("u1")).thenReturn(List.of());

        FamilyOrphanReport report = familyOrphanCleanupService.dryRun("u1");
        assertEquals(1, report.getPortfolioCount());
        assertEquals(100.0, report.getPortfolioValueSum());
        assertEquals("orphan", report.getPortfolios().get(0).getId());
    }

    @Test
    void applyDeletesOrphanBooks() {
        when(familyMemberService.listForOwner("u1")).thenReturn(List.of(
                FamilyMember.builder().id("fm-live").displayName("Other").build()));
        when(portfolioDocumentRepository.findByOwner("u1")).thenReturn(List.of(
                PortfolioDocument.builder().id("orphan").familyMemberId("gone").totalValue(9.0).build()));
        when(casImportRepository.findByOwnerOrderByAsOfDateDescImportedAtDesc("u1")).thenReturn(List.of());
        when(npsImportRepository.findByOwnerOrderByAsOfDateDescImportedAtDesc("u1")).thenReturn(List.of());

        FamilyOrphanReport report = familyOrphanCleanupService.apply("u1");
        assertEquals(1, report.getDeletedPortfolios());
        verify(brokerPortfolioDeleteService).deleteOwnedPortfolioByDocumentId("orphan", "u1");
    }
}