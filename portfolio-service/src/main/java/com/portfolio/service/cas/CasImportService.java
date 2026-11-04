package com.portfolio.service.cas;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;

import com.am.common.amcommondata.document.cas.CasImportDocument;
import com.am.common.amcommondata.model.cas.CasDocumentMeta;
import com.am.common.amcommondata.model.cas.CasMfFolioGroup;
import com.am.common.amcommondata.model.demat.DematAccountModel;
import com.am.common.amcommondata.model.family.FamilyMember;
import com.am.common.amcommondata.repository.cas.CasImportRepository;
import com.portfolio.model.events.CasDocumentEvent;
import com.portfolio.service.family.FamilyMemberService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class CasImportService {

    private final CasImportRepository casImportRepository;
    private final FamilyMemberService familyMemberService;

    public CasImportDocument upsertFromEvent(CasDocumentEvent event) {
        if (event == null || event.getUserId() == null || event.getUserId().isBlank()) {
            throw new IllegalArgumentException("CAS_DOCUMENT requires userId");
        }
        String owner = event.getUserId();

        String holderName = event.getHolderName();
        String holderEmail = event.getHolderEmail();
        String holderPan = event.getHolderPan();
        if ((holderName == null || holderName.isBlank()) && event.getAccountDirectory() != null) {
            holderName = event.getAccountDirectory().stream()
                    .filter(a -> a != null && a.getHolderName() != null && !a.getHolderName().isBlank())
                    .map(DematAccountModel::getHolderName)
                    .findFirst()
                    .orElse(null);
            if (holderEmail == null || holderEmail.isBlank()
                    || FamilyMemberService.looksTruncatedEmail(holderEmail)) {
                holderEmail = event.getAccountDirectory().stream()
                        .filter(a -> a != null && a.getEmail() != null && !a.getEmail().isBlank())
                        .filter(a -> !FamilyMemberService.looksTruncatedEmail(a.getEmail()))
                        .map(DematAccountModel::getEmail)
                        .findFirst()
                        .orElse(holderEmail);
            }
        }

        FamilyMember member = familyMemberService.resolveForImport(
                owner, event.getIdentityMatch(), holderName, holderEmail, holderPan, "CAS");

        // One CAS envelope per owner persona (primary vs familyMemberId).
        CasImportDocument existing;
        if (member != null && member.getId() != null) {
            existing = casImportRepository
                    .findFirstByOwnerAndFamilyMemberIdOrderByAsOfDateDescImportedAtDesc(
                            owner, member.getId())
                    .orElse(null);
            // Retag mis-scoped primary CAS that matches this family holder.
            existing = retagMatchingPrimary(owner, member, holderName, holderEmail, holderPan, existing);
        } else {
            existing = casImportRepository
                    .findFirstByOwnerAndFamilyMemberIdIsNullOrderByAsOfDateDescImportedAtDesc(owner)
                    .orElseGet(() -> casImportRepository
                            .findFirstByOwnerOrderByAsOfDateDescImportedAtDesc(owner)
                            .filter(d -> d.getFamilyMemberId() == null || d.getFamilyMemberId().isBlank())
                            .orElse(null));
        }

        CasImportDocument doc = existing != null ? existing : CasImportDocument.builder().build();
        doc.setOwner(owner);
        doc.setDocumentSource(event.getDocumentSource() != null ? event.getDocumentSource() : "CDSL_CAS");
        doc.setAsOfDate(event.getAsOfDate());
        doc.setStatementFrom(event.getStatementFrom());
        doc.setStatementTo(event.getStatementTo());
        doc.setImportedAt(LocalDateTime.now());
        doc.setCover(event.getCover());
        doc.setValidation(event.getValidation());
        doc.setAccountDirectory(event.getAccountDirectory());
        doc.setMfFolios(event.getMfFolios());
        doc.setUnmappedDematHoldings(event.getUnmappedDematHoldings());
        doc.setIdentityMatch(event.getIdentityMatch());
        doc.setHolderName(holderName);
        doc.setHolderEmail(holderEmail);
        doc.setHolderPan(FamilyMemberService.normPan(holderPan));
        if (member != null) {
            doc.setFamilyMemberId(member.getId());
            doc.setFamilyMemberName(member.getDisplayName());
        } else {
            doc.setFamilyMemberId(null);
            doc.setFamilyMemberName(null);
        }

        CasImportDocument saved = casImportRepository.save(doc);
        log.info("Upserted CAS import for owner={} asOf={} familyMemberId={} directoryRows={}",
                owner, saved.getAsOfDate(), saved.getFamilyMemberId(),
                saved.getAccountDirectory() != null ? saved.getAccountDirectory().size() : 0);
        return saved;
    }

    /**
     * If a primary (null familyMemberId) CAS document matches this family holder,
     * reuse it as the family doc (or delete the twin when a family doc already exists).
     */
    CasImportDocument retagMatchingPrimary(
            String owner,
            FamilyMember member,
            String holderName,
            String holderEmail,
            String holderPan,
            CasImportDocument existingFamily) {
        if (member == null || member.getId() == null) {
            return existingFamily;
        }
        CasImportDocument primary = casImportRepository
                .findFirstByOwnerAndFamilyMemberIdIsNullOrderByAsOfDateDescImportedAtDesc(owner)
                .orElse(null);
        if (primary == null || !matchesFamilyHolder(primary, holderName, holderEmail, holderPan, member)) {
            return existingFamily;
        }
        if (existingFamily == null) {
            log.info("Retagging primary CAS {} → familyMemberId={}", primary.getId(), member.getId());
            primary.setFamilyMemberId(member.getId());
            primary.setFamilyMemberName(member.getDisplayName());
            return casImportRepository.save(primary);
        }
        if (!Objects.equals(existingFamily.getId(), primary.getId())) {
            log.info("Deleting mis-scoped primary CAS twin {} (family doc {})",
                    primary.getId(), existingFamily.getId());
            casImportRepository.delete(primary);
        }
        return existingFamily;
    }

    static boolean matchesFamilyHolder(
            CasImportDocument primary,
            String holderName,
            String holderEmail,
            String holderPan,
            FamilyMember member) {
        if (primary == null) {
            return false;
        }
        String panNorm = FamilyMemberService.normPan(holderPan);
        String primaryPan = FamilyMemberService.normPan(primary.getHolderPan());
        if (panNorm != null && panNorm.equals(primaryPan)) {
            return true;
        }
        if (panNorm != null && panNorm.equalsIgnoreCase(nullToEmpty(member.getPan()))) {
            // Member already known by PAN; primary holder name/email also align if present.
            String pe = normEmail(primary.getHolderEmail());
            String he = normEmail(holderEmail);
            if (pe != null && pe.equals(he)) {
                return true;
            }
            String pn = normName(primary.getHolderName());
            String hn = normName(holderName);
            if (pn != null && pn.equals(hn)) {
                return true;
            }
            if (pn != null && pn.equals(normName(member.getDisplayName()))) {
                return true;
            }
        }
        String pe = normEmail(primary.getHolderEmail());
        String he = normEmail(holderEmail);
        if (pe != null && pe.equals(he) && !FamilyMemberService.looksTruncatedEmail(pe)) {
            return true;
        }
        String me = normEmail(member.getEmail());
        if (pe != null && pe.equals(me) && !FamilyMemberService.looksTruncatedEmail(pe)) {
            return true;
        }
        String pn = normName(primary.getHolderName());
        String hn = normName(holderName);
        if (pn != null && (pn.equals(hn) || pn.equals(normName(member.getDisplayName())))) {
            return true;
        }
        // Directory row email/name fallback
        if (primary.getAccountDirectory() != null) {
            for (DematAccountModel a : primary.getAccountDirectory()) {
                if (a == null) {
                    continue;
                }
                String ae = normEmail(a.getEmail());
                if (ae != null && (ae.equals(he) || ae.equals(me))
                        && !FamilyMemberService.looksTruncatedEmail(ae)) {
                    return true;
                }
                String an = normName(a.getHolderName());
                if (an != null && (an.equals(hn) || an.equals(normName(member.getDisplayName())))) {
                    return true;
                }
            }
        }
        return false;
    }

    public Optional<CasImportDocument> latest(String userId) {
        return casImportRepository.findFirstByOwnerOrderByAsOfDateDescImportedAtDesc(userId);
    }

    public List<DematAccountModel> accounts(String userId) {
        return accountsForOwnerAllPersonas(userId);
    }

    /**
     * Flatten every CAS import for the owner; stamp each demat/MF row with the
     * parent document's {@code familyMemberId} (null = primary).
     * One latest import per persona (primary vs each family id).
     */
    public List<DematAccountModel> accountsForOwnerAllPersonas(String userId) {
        if (userId == null || userId.isBlank()) {
            return Collections.emptyList();
        }
        List<CasImportDocument> all =
                casImportRepository.findByOwnerOrderByAsOfDateDescImportedAtDesc(userId);
        if (all == null || all.isEmpty()) {
            return Collections.emptyList();
        }
        Map<String, CasImportDocument> latestByPersona = new LinkedHashMap<>();
        for (CasImportDocument doc : all) {
            if (doc == null) {
                continue;
            }
            String key = blankToNull(doc.getFamilyMemberId());
            String mapKey = key == null ? "" : key;
            latestByPersona.putIfAbsent(mapKey, doc);
        }
        List<DematAccountModel> out = new ArrayList<>();
        for (CasImportDocument doc : latestByPersona.values()) {
            String fmId = blankToNull(doc.getFamilyMemberId());
            List<DematAccountModel> directory = new ArrayList<>();
            if (doc.getAccountDirectory() != null) {
                for (DematAccountModel a : doc.getAccountDirectory()) {
                    if (a == null) {
                        continue;
                    }
                    directory.add(copyWithFamily(a, fmId));
                }
            }
            mergeMissingMfFolios(directory, doc.getMfFolios(), fmId);
            out.addAll(directory);
        }
        return out;
    }

    static void mergeMissingMfFolios(
            List<DematAccountModel> demat, List<CasMfFolioGroup> folios, String familyMemberId) {
        if (folios == null || folios.isEmpty()) {
            return;
        }
        for (CasMfFolioGroup g : folios) {
            if (g == null || g.getFolio() == null || g.getFolio().isBlank()) {
                continue;
            }
            String folio = g.getFolio().trim();
            boolean exists = demat.stream().anyMatch(a ->
                    a != null
                            && "mf".equalsIgnoreCase(nullToEmpty(a.getAccountType()))
                            && folio.equalsIgnoreCase(nullToEmpty(a.getFolio())));
            if (exists) {
                continue;
            }
            demat.add(DematAccountModel.builder()
                    .accountNo(folio)
                    .accountType("mf")
                    .folio(folio)
                    .amcName(g.getAmcName())
                    .active(true)
                    .status("ACTIVE")
                    .familyMemberId(familyMemberId)
                    .build());
        }
    }

    private static DematAccountModel copyWithFamily(DematAccountModel a, String familyMemberId) {
        return DematAccountModel.builder()
                .accountNo(a.getAccountNo())
                .accountType(a.getAccountType())
                .status(a.getStatus())
                .email(a.getEmail())
                .mobile(a.getMobile())
                .holderName(a.getHolderName())
                .dpId(a.getDpId())
                .clientId(a.getClientId())
                .boid(a.getBoid())
                .dpName(a.getDpName())
                .nominee(a.getNominee())
                .nomineeRegistered(a.getNomineeRegistered())
                .frozenStatus(a.getFrozenStatus())
                .holdingValue(a.getHoldingValue())
                .mappedBroker(a.getMappedBroker())
                .amcName(a.getAmcName())
                .folio(a.getFolio())
                .active(a.getActive())
                .portfolioId(a.getPortfolioId())
                .familyMemberId(familyMemberId)
                .build();
    }

    public Optional<DematAccountModel> account(String userId, String accountNo) {
        if (accountNo == null) {
            return Optional.empty();
        }
        return accounts(userId).stream()
                .filter(a -> accountNo.equals(a.getAccountNo())
                        || accountNo.equals(a.getBoid())
                        || accountNo.equals(a.getFolio()))
                .findFirst();
    }

    public List<CasMfFolioGroup> folios(String userId) {
        return latest(userId)
                .map(CasImportDocument::getMfFolios)
                .orElse(Collections.emptyList());
    }

    public Optional<CasMfFolioGroup> folio(String userId, String folio) {
        if (folio == null) {
            return Optional.empty();
        }
        return folios(userId).stream()
                .filter(f -> folio.equals(f.getFolio()))
                .findFirst();
    }

    public Optional<CasDocumentMeta> latestMeta(String userId) {
        return latest(userId).map(doc -> CasDocumentMeta.builder()
                .documentSource(doc.getDocumentSource())
                .asOfDate(doc.getAsOfDate())
                .statementFrom(doc.getStatementFrom())
                .statementTo(doc.getStatementTo())
                .importedAt(doc.getImportedAt())
                .cover(doc.getCover())
                .validation(doc.getValidation())
                .build());
    }

    public List<CasMfFolioGroup> foliosForPortfolio(String userId, String portfolioIdHint) {
        return folios(userId).stream()
                .filter(f -> f != null)
                .collect(Collectors.toList());
    }

    private static String blankToNull(String s) {
        if (s == null || s.isBlank()) {
            return null;
        }
        return s.trim();
    }

    private static String nullToEmpty(String s) {
        return s == null ? "" : s;
    }

    private static String normEmail(String email) {
        if (email == null || email.isBlank()) {
            return null;
        }
        return email.trim().toLowerCase(Locale.ROOT);
    }

    private static String normName(String name) {
        if (name == null || name.isBlank()) {
            return null;
        }
        return name.trim().toLowerCase(Locale.ROOT).replaceAll("\\s+", " ");
    }
}
