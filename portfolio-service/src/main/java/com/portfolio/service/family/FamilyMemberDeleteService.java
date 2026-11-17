package com.portfolio.service.family;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import com.am.common.amcommondata.document.cas.CasImportDocument;
import com.am.common.amcommondata.document.family.FamilyMemberDocument;
import com.am.common.amcommondata.document.nps.NpsImportDocument;
import com.am.common.amcommondata.document.portfolio.PortfolioDocument;
import com.am.common.amcommondata.model.family.FamilyMember;
import com.am.common.amcommondata.repository.cas.CasImportRepository;
import com.am.common.amcommondata.repository.family.FamilyMemberRepository;
import com.am.common.amcommondata.repository.nps.NpsImportRepository;
import com.am.common.amcommondata.repository.portfolio.PortfolioDocumentRepository;
import com.portfolio.service.accounts.AccountsHubService;
import com.portfolio.service.portfolio.BrokerPortfolioDeleteService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Cascade-delete a soft family persona: tagged broker books, CAS/NPS imports, then the member.
 * Also removes orphan same-nameNorm stamps left by prior create/delete UUID churn.
 * Primary (null familyMemberId) books and CAS are never touched.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FamilyMemberDeleteService {

    private final FamilyMemberRepository familyMemberRepository;
    private final PortfolioDocumentRepository portfolioDocumentRepository;
    private final CasImportRepository casImportRepository;
    private final NpsImportRepository npsImportRepository;
    private final BrokerPortfolioDeleteService brokerPortfolioDeleteService;
    private final FamilyMemberService familyMemberService;

    public void deleteOwnedMember(String familyMemberId, String ownerUserId) {
        if (ownerUserId == null || ownerUserId.isBlank()) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Missing user");
        }
        if (familyMemberId == null || familyMemberId.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid familyMemberId");
        }

        familyMemberRepository.consolidateObjectIdStringTwins(ownerUserId);

        FamilyMemberDocument member = familyMemberRepository
                .findOwnedById(familyMemberId, ownerUserId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Family member not found"));

        String nameNorm = member.getNameNorm() != null && !member.getNameNorm().isBlank()
                ? member.getNameNorm()
                : FamilyPersonaScope.normName(member.getDisplayName());

        Set<String> deleteIds = new HashSet<>();
        deleteIds.add(member.getId().trim());
        deleteIds.add(familyMemberId.trim());

        Set<String> liveAfter = liveMemberIdsExcept(ownerUserId, deleteIds);

        List<PortfolioDocument> allBooks = portfolioDocumentRepository.findByOwner(ownerUserId);
        if (allBooks == null) {
            allBooks = List.of();
        }
        List<String> bookIds = new ArrayList<>();
        for (PortfolioDocument doc : allBooks) {
            if (doc == null || doc.getId() == null) {
                continue;
            }
            if (shouldCascadePortfolio(doc, deleteIds, liveAfter, nameNorm)) {
                bookIds.add(doc.getId());
            }
        }

        for (String bookId : bookIds) {
            try {
                brokerPortfolioDeleteService.deleteOwnedPortfolioByDocumentId(bookId, ownerUserId);
            } catch (ResponseStatusException ex) {
                if (ex.getStatusCode() == HttpStatus.NOT_FOUND) {
                    log.warn("Family cascade: portfolio {} already gone for owner={}", bookId, ownerUserId);
                    continue;
                }
                throw ex;
            }
        }

        long casDeleted = 0L;
        long npsDeleted = 0L;
        Set<String> casFmIds = new HashSet<>(deleteIds);
        Set<String> npsFmIds = new HashSet<>(deleteIds);

        List<CasImportDocument> casDocs =
                casImportRepository.findByOwnerOrderByAsOfDateDescImportedAtDesc(ownerUserId);
        if (casDocs != null) {
            for (CasImportDocument doc : casDocs) {
                if (doc == null || FamilyPersonaScope.isPrimary(doc.getFamilyMemberId())) {
                    continue;
                }
                String fm = doc.getFamilyMemberId().trim();
                if (deleteIds.contains(fm)
                        || (FamilyPersonaScope.isOrphanStamp(fm, liveAfter)
                                && FamilyPersonaScope.nameNormEquals(doc.getFamilyMemberName(), nameNorm))) {
                    casFmIds.add(fm);
                }
            }
        }
        for (String fm : casFmIds) {
            casDeleted += casImportRepository.deleteByOwnerAndFamilyMemberId(ownerUserId, fm);
        }

        List<NpsImportDocument> npsDocs =
                npsImportRepository.findByOwnerOrderByAsOfDateDescImportedAtDesc(ownerUserId);
        if (npsDocs != null) {
            for (NpsImportDocument doc : npsDocs) {
                if (doc == null || FamilyPersonaScope.isPrimary(doc.getFamilyMemberId())) {
                    continue;
                }
                String fm = doc.getFamilyMemberId().trim();
                if (deleteIds.contains(fm)
                        || (FamilyPersonaScope.isOrphanStamp(fm, liveAfter)
                                && FamilyPersonaScope.nameNormEquals(doc.getFamilyMemberName(), nameNorm))) {
                    npsFmIds.add(fm);
                }
            }
        }
        for (String fm : npsFmIds) {
            npsDeleted += npsImportRepository.deleteByOwnerAndFamilyMemberId(ownerUserId, fm);
        }

        familyMemberRepository.delete(member);

        assertNoLeftovers(ownerUserId, deleteIds, liveAfter, nameNorm);

        AccountsHubService.invalidateXirrCache(ownerUserId);
        log.info(
                "Deleted family member id={} owner={} books={} casDocs={} npsDocs={}",
                familyMemberId, ownerUserId, bookIds.size(), casDeleted, npsDeleted);
    }

    private static boolean shouldCascadePortfolio(
            PortfolioDocument doc,
            Set<String> deleteIds,
            Set<String> liveAfter,
            String nameNorm) {
        if (FamilyPersonaScope.isPrimary(doc.getFamilyMemberId())) {
            return false;
        }
        String fm = doc.getFamilyMemberId().trim();
        if (deleteIds.contains(fm)) {
            return true;
        }
        return FamilyPersonaScope.isOrphanStamp(fm, liveAfter)
                && FamilyPersonaScope.nameNormEquals(doc.getFamilyMemberName(), nameNorm);
    }

    private void assertNoLeftovers(
            String ownerUserId,
            Set<String> deleteIds,
            Set<String> liveAfter,
            String nameNorm) {
        List<PortfolioDocument> remaining = portfolioDocumentRepository.findByOwner(ownerUserId);
        int leftover = 0;
        if (remaining != null) {
            for (PortfolioDocument doc : remaining) {
                if (doc == null || FamilyPersonaScope.isPrimary(doc.getFamilyMemberId())) {
                    continue;
                }
                if (shouldCascadePortfolio(doc, deleteIds, liveAfter, nameNorm)) {
                    leftover++;
                }
            }
        }
        if (leftover > 0) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Family delete incomplete: " + leftover + " tagged portfolio(s) remain");
        }
    }

    private Set<String> liveMemberIdsExcept(String ownerUserId, Set<String> exclude) {
        List<FamilyMember> members = familyMemberService.listForOwner(ownerUserId);
        List<String> ids = new ArrayList<>();
        if (members != null) {
            for (FamilyMember m : members) {
                if (m == null || m.getId() == null || m.getId().isBlank()) {
                    continue;
                }
                String id = m.getId().trim();
                if (exclude != null && exclude.contains(id)) {
                    continue;
                }
                ids.add(id);
            }
        }
        return FamilyPersonaScope.liveIds(ids);
    }
}