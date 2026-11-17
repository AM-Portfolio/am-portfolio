package com.portfolio.service.family;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import com.am.common.amcommondata.document.cas.CasImportDocument;
import com.am.common.amcommondata.document.family.FamilyMemberDocument;
import com.am.common.amcommondata.document.nps.NpsImportDocument;
import com.am.common.amcommondata.document.portfolio.PortfolioDocument;
import com.am.common.amcommondata.model.enums.PortfolioKind;
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
 * Merges twin family personas (same nameNorm, different ids) into one canonical member and stamps.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FamilyMemberConsolidationService {

    private final FamilyMemberRepository familyMemberRepository;
    private final PortfolioDocumentRepository portfolioDocumentRepository;
    private final CasImportRepository casImportRepository;
    private final NpsImportRepository npsImportRepository;
    private final BrokerPortfolioDeleteService brokerPortfolioDeleteService;

    public FamilyMemberConsolidationReport dryRun(String ownerUserId, String canonicalMemberId) {
        return consolidate(ownerUserId, canonicalMemberId, false);
    }

    public FamilyMemberConsolidationReport apply(String ownerUserId, String canonicalMemberId) {
        return consolidate(ownerUserId, canonicalMemberId, true);
    }

    /** Idempotent repair after {@link FamilyMemberService#resolveForImport}. */
    public void consolidateAfterResolve(String ownerUserId, FamilyMember canonical) {
        if (ownerUserId == null || ownerUserId.isBlank() || canonical == null || canonical.getId() == null) {
            return;
        }
        FamilyMemberConsolidationReport report = consolidate(ownerUserId, canonical.getId(), true);
        if (report.getRetaggedPortfolios() > 0
                || report.getMergedBrokerBooksDeleted() > 0
                || report.getDeletedDuplicateMembers() > 0) {
            log.info(
                    "Consolidated family persona owner={} canonical={} report={}",
                    ownerUserId,
                    canonical.getId(),
                    report);
        }
    }

    private FamilyMemberConsolidationReport consolidate(
            String ownerUserId, String canonicalMemberId, boolean apply) {
        if (ownerUserId == null || ownerUserId.isBlank()) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Missing user");
        }
        if (canonicalMemberId == null || canonicalMemberId.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid familyMemberId");
        }
        familyMemberRepository.consolidateObjectIdStringTwins(ownerUserId);

        FamilyMemberDocument canonical = familyMemberRepository
                .findOwnedById(canonicalMemberId, ownerUserId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Family member not found"));

        String nameNorm = canonical.getNameNorm() != null && !canonical.getNameNorm().isBlank()
                ? canonical.getNameNorm()
                : FamilyPersonaScope.normName(canonical.getDisplayName());
        if (nameNorm == null || nameNorm.isBlank()) {
            return FamilyMemberConsolidationReport.builder()
                    .canonicalMemberId(canonicalMemberId)
                    .applied(apply)
                    .build();
        }

        String canonicalName = canonical.getDisplayName();
        Set<String> liveIds = liveMemberIdsExcept(ownerUserId, Set.of());
        List<String> duplicateMemberIds = duplicateMemberIds(ownerUserId, canonicalMemberId, nameNorm);

        int retagPortfolios = 0;
        int retagCas = 0;
        int retagNps = 0;
        List<PortfolioDocument> books = portfolioDocumentRepository.findByOwner(ownerUserId);
        if (books == null) {
            books = List.of();
        }
        for (PortfolioDocument doc : books) {
            if (doc == null || FamilyPersonaScope.isPrimary(doc.getFamilyMemberId())) {
                continue;
            }
            if (!shouldRetagStamp(doc.getFamilyMemberId(), doc.getFamilyMemberName(), nameNorm, canonicalMemberId, liveIds)) {
                continue;
            }
            retagPortfolios++;
            if (apply) {
                doc.setFamilyMemberId(canonicalMemberId);
                if (canonicalName != null && !canonicalName.isBlank()) {
                    doc.setFamilyMemberName(canonicalName);
                }
                portfolioDocumentRepository.save(doc);
            }
        }

        List<CasImportDocument> casDocs =
                casImportRepository.findByOwnerOrderByAsOfDateDescImportedAtDesc(ownerUserId);
        if (casDocs != null) {
            for (CasImportDocument doc : casDocs) {
                if (doc == null || FamilyPersonaScope.isPrimary(doc.getFamilyMemberId())) {
                    continue;
                }
                if (!matchesPersonaStamp(
                        doc.getFamilyMemberId(), doc.getFamilyMemberName(), doc.getHolderName(), nameNorm, canonicalMemberId, liveIds)) {
                    continue;
                }
                retagCas++;
                if (apply) {
                    doc.setFamilyMemberId(canonicalMemberId);
                    if (canonicalName != null && !canonicalName.isBlank()) {
                        doc.setFamilyMemberName(canonicalName);
                    }
                    casImportRepository.save(doc);
                }
            }
        }

        List<NpsImportDocument> npsDocs =
                npsImportRepository.findByOwnerOrderByAsOfDateDescImportedAtDesc(ownerUserId);
        if (npsDocs != null) {
            for (NpsImportDocument doc : npsDocs) {
                if (doc == null || FamilyPersonaScope.isPrimary(doc.getFamilyMemberId())) {
                    continue;
                }
                if (!matchesPersonaStamp(
                        doc.getFamilyMemberId(), doc.getFamilyMemberName(), doc.getHolderName(), nameNorm, canonicalMemberId, liveIds)) {
                    continue;
                }
                retagNps++;
                if (apply) {
                    doc.setFamilyMemberId(canonicalMemberId);
                    if (canonicalName != null && !canonicalName.isBlank()) {
                        doc.setFamilyMemberName(canonicalName);
                    }
                    npsImportRepository.save(doc);
                }
            }
        }

        int mergedDeletes = 0;
        if (apply) {
            books = portfolioDocumentRepository.findByOwner(ownerUserId);
            if (books == null) {
                books = List.of();
            }
        }
        Map<String, List<PortfolioDocument>> brokerGroups = groupCanonicalBrokerBooks(books, ownerUserId, canonicalMemberId);
        List<String> deleteBookIds = pickTwinDeletes(brokerGroups);
        mergedDeletes = deleteBookIds.size();
        if (apply) {
            for (String bookId : deleteBookIds) {
                try {
                    brokerPortfolioDeleteService.deleteOwnedPortfolioByDocumentId(bookId, ownerUserId);
                } catch (ResponseStatusException ex) {
                    if (ex.getStatusCode() != HttpStatus.NOT_FOUND) {
                        throw ex;
                    }
                }
            }
        }

        int deletedMembers = duplicateMemberIds.size();
        if (apply) {
            for (String dupId : duplicateMemberIds) {
                familyMemberRepository.findOwnedById(dupId, ownerUserId).ifPresent(familyMemberRepository::delete);
            }
            if (retagPortfolios > 0 || mergedDeletes > 0 || deletedMembers > 0) {
                AccountsHubService.invalidateXirrCache(ownerUserId);
            }
        }

        return FamilyMemberConsolidationReport.builder()
                .canonicalMemberId(canonicalMemberId)
                .nameNorm(nameNorm)
                .duplicateMemberIds(duplicateMemberIds)
                .retaggedPortfolios(retagPortfolios)
                .retaggedCasImports(retagCas)
                .retaggedNpsImports(retagNps)
                .mergedBrokerBooksDeleted(mergedDeletes)
                .deletedDuplicateMembers(deletedMembers)
                .applied(apply)
                .build();
    }

    private static boolean matchesPersonaStamp(
            String familyMemberId,
            String stampName,
            String holderName,
            String nameNorm,
            String canonicalMemberId,
            Set<String> liveMemberIds) {
        if (FamilyPersonaScope.isPrimary(familyMemberId)) {
            return false;
        }
        if (canonicalMemberId.equals(familyMemberId != null ? familyMemberId.trim() : "")) {
            return false;
        }
        boolean nameMatch = FamilyPersonaScope.nameNormEquals(stampName, nameNorm)
                || FamilyPersonaScope.nameNormEquals(holderName, nameNorm);
        if (!nameMatch) {
            return false;
        }
        return FamilyPersonaScope.isOrphanStamp(familyMemberId, liveMemberIds)
                || !canonicalMemberId.equals(familyMemberId.trim());
    }

    private static boolean shouldRetagStamp(
            String familyMemberId,
            String stampName,
            String nameNorm,
            String canonicalMemberId,
            Set<String> liveMemberIds) {
        return matchesPersonaStamp(familyMemberId, stampName, null, nameNorm, canonicalMemberId, liveMemberIds);
    }

    private List<String> duplicateMemberIds(String ownerUserId, String canonicalMemberId, String nameNorm) {
        List<FamilyMemberDocument> docs =
                familyMemberRepository.findByOwnerUserIdOrderByDisplayNameAsc(ownerUserId);
        List<String> out = new ArrayList<>();
        if (docs == null) {
            return out;
        }
        String canon = canonicalMemberId.trim();
        for (FamilyMemberDocument doc : docs) {
            if (doc == null || doc.getId() == null) {
                continue;
            }
            if (canon.equals(doc.getId().trim())) {
                continue;
            }
            String nn = doc.getNameNorm() != null ? doc.getNameNorm() : FamilyPersonaScope.normName(doc.getDisplayName());
            if (Objects.equals(nameNorm, nn)) {
                out.add(doc.getId().trim());
            }
        }
        return out;
    }

    private Map<String, List<PortfolioDocument>> groupCanonicalBrokerBooks(
            List<PortfolioDocument> books, String ownerUserId, String canonicalMemberId) {
        Map<String, List<PortfolioDocument>> groups = new LinkedHashMap<>();
        if (books == null) {
            return groups;
        }
        for (PortfolioDocument doc : books) {
            if (doc == null || doc.getId() == null || doc.getOwner() == null) {
                continue;
            }
            if (!ownerUserId.equals(doc.getOwner())) {
                continue;
            }
            if (!PortfolioKind.BROKER.equals(doc.getPortfolioKind())) {
                continue;
            }
            if (FamilyPersonaScope.isPrimary(doc.getFamilyMemberId())
                    || !canonicalMemberId.equals(doc.getFamilyMemberId().trim())) {
                continue;
            }
            String key = brokerGroupKey(doc);
            groups.computeIfAbsent(key, k -> new ArrayList<>()).add(doc);
        }
        return groups;
    }

    private static String brokerGroupKey(PortfolioDocument doc) {
        String broker = doc.getBrokerType() != null ? doc.getBrokerType().name() : "UNKNOWN";
        String boid = doc.getDematBoid() != null ? doc.getDematBoid().trim().toLowerCase(Locale.ROOT) : "";
        return broker + "|" + boid;
    }

    private List<String> pickTwinDeletes(Map<String, List<PortfolioDocument>> groups) {
        List<String> deleteIds = new ArrayList<>();
        for (List<PortfolioDocument> group : groups.values()) {
            if (group == null || group.size() <= 1) {
                continue;
            }
            PortfolioDocument keep = pickCanonicalBook(group);
            for (PortfolioDocument doc : group) {
                if (doc == null || doc.getId() == null) {
                    continue;
                }
                if (!doc.getId().equals(keep.getId())) {
                    deleteIds.add(doc.getId());
                }
            }
        }
        return deleteIds;
    }

    private static PortfolioDocument pickCanonicalBook(List<PortfolioDocument> docs) {
        return docs.stream()
                .max(Comparator
                        .comparing((PortfolioDocument p) -> p.getTotalValue() != null ? p.getTotalValue() : 0.0)
                        .thenComparing(p -> p.getAudit() != null && p.getAudit().getUpdatedAt() != null
                                ? p.getAudit().getUpdatedAt()
                                : LocalDateTime.MIN))
                .orElse(docs.get(0));
    }

    private Set<String> liveMemberIdsExcept(String ownerUserId, Set<String> exclude) {
        List<FamilyMemberDocument> docs =
                familyMemberRepository.findByOwnerUserIdOrderByDisplayNameAsc(ownerUserId);
        List<String> ids = new ArrayList<>();
        if (docs != null) {
            for (FamilyMemberDocument doc : docs) {
                if (doc == null || doc.getId() == null || doc.getId().isBlank()) {
                    continue;
                }
                String id = doc.getId().trim();
                if (exclude != null && exclude.contains(id)) {
                    continue;
                }
                ids.add(id);
            }
        }
        return FamilyPersonaScope.liveIds(ids);
    }
}