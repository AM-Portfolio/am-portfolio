package com.portfolio.service.family;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import com.am.common.amcommondata.document.cas.CasImportDocument;
import com.am.common.amcommondata.document.nps.NpsImportDocument;
import com.am.common.amcommondata.document.portfolio.PortfolioDocument;
import com.am.common.amcommondata.model.family.FamilyMember;
import com.am.common.amcommondata.repository.cas.CasImportRepository;
import com.am.common.amcommondata.repository.nps.NpsImportRepository;
import com.am.common.amcommondata.repository.portfolio.PortfolioDocumentRepository;
import com.portfolio.service.accounts.AccountsHubService;
import com.portfolio.service.portfolio.BrokerPortfolioDeleteService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Lists / deletes portfolio + CAS/NPS stamps whose familyMemberId is not in live family_members.
 * Primary (blank familyMemberId) is never touched.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FamilyOrphanCleanupService {

    private final FamilyMemberService familyMemberService;
    private final PortfolioDocumentRepository portfolioDocumentRepository;
    private final CasImportRepository casImportRepository;
    private final NpsImportRepository npsImportRepository;
    private final BrokerPortfolioDeleteService brokerPortfolioDeleteService;

    public FamilyOrphanReport dryRun(String ownerUserId) {
        return scan(ownerUserId, false);
    }

    public FamilyOrphanReport apply(String ownerUserId) {
        return scan(ownerUserId, true);
    }

    /** True when any orphan stamp shares this nameNorm (blocks recreate-storm). */
    public boolean hasOrphansForNameNorm(String ownerUserId, String nameNorm) {
        if (ownerUserId == null || ownerUserId.isBlank() || nameNorm == null || nameNorm.isBlank()) {
            return false;
        }
        Set<String> live = liveMemberIds(ownerUserId);
        List<PortfolioDocument> books = portfolioDocumentRepository.findByOwner(ownerUserId);
        if (books != null) {
            for (PortfolioDocument doc : books) {
                if (doc == null) {
                    continue;
                }
                if (FamilyPersonaScope.isOrphanStamp(doc.getFamilyMemberId(), live)
                        && FamilyPersonaScope.nameNormEquals(doc.getFamilyMemberName(), nameNorm)) {
                    return true;
                }
            }
        }
        List<CasImportDocument> cas = casImportRepository.findByOwnerOrderByAsOfDateDescImportedAtDesc(ownerUserId);
        if (cas != null) {
            for (CasImportDocument doc : cas) {
                if (doc == null) {
                    continue;
                }
                if (FamilyPersonaScope.isOrphanStamp(doc.getFamilyMemberId(), live)
                        && FamilyPersonaScope.nameNormEquals(doc.getFamilyMemberName(), nameNorm)) {
                    return true;
                }
            }
        }
        return false;
    }

    private FamilyOrphanReport scan(String ownerUserId, boolean apply) {
        if (ownerUserId == null || ownerUserId.isBlank()) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Missing user");
        }
        Set<String> live = liveMemberIds(ownerUserId);
        List<PortfolioDocument> books = portfolioDocumentRepository.findByOwner(ownerUserId);
        if (books == null) {
            books = List.of();
        }
        List<FamilyOrphanReport.OrphanPortfolioRow> orphanRows = new ArrayList<>();
        List<String> orphanBookIds = new ArrayList<>();
        double valueSum = 0.0;
        for (PortfolioDocument doc : books) {
            if (doc == null || doc.getId() == null) {
                continue;
            }
            if (!FamilyPersonaScope.isOrphanStamp(doc.getFamilyMemberId(), live)) {
                continue;
            }
            orphanBookIds.add(doc.getId());
            Double tv = doc.getTotalValue();
            if (tv != null && tv > 0) {
                valueSum += tv;
            }
            orphanRows.add(FamilyOrphanReport.OrphanPortfolioRow.builder()
                    .id(doc.getId())
                    .name(doc.getName())
                    .brokerType(doc.getBrokerType() != null ? doc.getBrokerType().name() : null)
                    .familyMemberId(doc.getFamilyMemberId())
                    .familyMemberName(doc.getFamilyMemberName())
                    .totalValue(tv)
                    .build());
        }

        List<CasImportDocument> casDocs =
                casImportRepository.findByOwnerOrderByAsOfDateDescImportedAtDesc(ownerUserId);
        if (casDocs == null) {
            casDocs = List.of();
        }
        List<String> orphanCasIds = new ArrayList<>();
        Set<String> orphanCasFmIds = new HashSet<>();
        for (CasImportDocument doc : casDocs) {
            if (doc == null || doc.getId() == null) {
                continue;
            }
            if (!FamilyPersonaScope.isOrphanStamp(doc.getFamilyMemberId(), live)) {
                continue;
            }
            orphanCasIds.add(doc.getId());
            orphanCasFmIds.add(doc.getFamilyMemberId().trim());
        }

        List<NpsImportDocument> npsDocs =
                npsImportRepository.findByOwnerOrderByAsOfDateDescImportedAtDesc(ownerUserId);
        if (npsDocs == null) {
            npsDocs = List.of();
        }
        List<String> orphanNpsIds = new ArrayList<>();
        Set<String> orphanNpsFmIds = new HashSet<>();
        for (NpsImportDocument doc : npsDocs) {
            if (doc == null || doc.getId() == null) {
                continue;
            }
            if (!FamilyPersonaScope.isOrphanStamp(doc.getFamilyMemberId(), live)) {
                continue;
            }
            orphanNpsIds.add(doc.getId());
            orphanNpsFmIds.add(doc.getFamilyMemberId().trim());
        }

        FamilyOrphanReport.FamilyOrphanReportBuilder report = FamilyOrphanReport.builder()
                .portfolioCount(orphanBookIds.size())
                .casImportCount(orphanCasIds.size())
                .npsImportCount(orphanNpsIds.size())
                .portfolioValueSum(round2(valueSum))
                .portfolios(orphanRows)
                .casImportIds(orphanCasIds)
                .npsImportIds(orphanNpsIds);

        if (!apply) {
            return report.build();
        }

        int deletedBooks = 0;
        for (String bookId : orphanBookIds) {
            try {
                brokerPortfolioDeleteService.deleteOwnedPortfolioByDocumentId(bookId, ownerUserId);
                deletedBooks++;
            } catch (ResponseStatusException ex) {
                if (ex.getStatusCode() == HttpStatus.NOT_FOUND) {
                    log.warn("Orphan cleanup: portfolio {} already gone for owner={}", bookId, ownerUserId);
                    continue;
                }
                throw ex;
            }
        }

        long deletedCas = 0L;
        for (String fmId : orphanCasFmIds) {
            deletedCas += casImportRepository.deleteByOwnerAndFamilyMemberId(ownerUserId, fmId);
        }
        long deletedNps = 0L;
        for (String fmId : orphanNpsFmIds) {
            deletedNps += npsImportRepository.deleteByOwnerAndFamilyMemberId(ownerUserId, fmId);
        }

        AccountsHubService.invalidateXirrCache(ownerUserId);
        log.info(
                "Orphan cleanup applied owner={} books={} cas={} nps={}",
                ownerUserId, deletedBooks, deletedCas, deletedNps);

        return report
                .deletedPortfolios(deletedBooks)
                .deletedCasImports((int) deletedCas)
                .deletedNpsImports((int) deletedNps)
                .build();
    }

    private Set<String> liveMemberIds(String ownerUserId) {
        List<FamilyMember> members = familyMemberService.listForOwner(ownerUserId);
        List<String> ids = new ArrayList<>();
        if (members != null) {
            for (FamilyMember m : members) {
                if (m != null && m.getId() != null) {
                    ids.add(m.getId());
                }
            }
        }
        return FamilyPersonaScope.liveIds(ids);
    }

    private static double round2(double v) {
        return Math.round(v * 100.0) / 100.0;
    }
}