package com.portfolio.service.family;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import com.am.common.amcommondata.document.cas.CasImportDocument;
import com.am.common.amcommondata.document.family.FamilyMemberDocument;
import com.am.common.amcommondata.model.demat.DematAccountModel;
import com.am.common.amcommondata.model.family.FamilyMember;
import com.am.common.amcommondata.repository.cas.CasImportRepository;
import com.am.common.amcommondata.repository.family.FamilyMemberRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class FamilyMemberService {

    /** Soft family personas per owner account (manual + CAS/NPS). */
    public static final int MAX_FAMILY_MEMBERS = 5;

    private final FamilyMemberRepository familyMemberRepository;
    private final CasImportRepository casImportRepository;

    public List<FamilyMember> listForOwner(String ownerUserId) {
        if (ownerUserId != null && !ownerUserId.isBlank()) {
            familyMemberRepository.consolidateObjectIdStringTwins(ownerUserId);
        }
        List<FamilyMemberDocument> docs =
                familyMemberRepository.findByOwnerUserIdOrderByDisplayNameAsc(ownerUserId);
        // Dedupe by id string (belt-and-suspenders after consolidate).
        Map<String, FamilyMemberDocument> byId = new LinkedHashMap<>();
        for (FamilyMemberDocument doc : docs) {
            if (doc == null || doc.getId() == null) {
                continue;
            }
            FamilyMemberDocument prev = byId.get(doc.getId());
            if (prev == null || prefer(doc, prev)) {
                byId.put(doc.getId(), doc);
            }
        }
        return byId.values().stream()
                .map(doc -> toModel(doc, countDematAccounts(ownerUserId, doc.getId())))
                .collect(Collectors.toList());
    }

    public FamilyMember getOwned(String familyMemberId, String ownerUserId) {
        FamilyMemberDocument doc = requireOwned(familyMemberId, ownerUserId);
        return toModel(doc, countDematAccounts(ownerUserId, doc.getId()));
    }

    /**
     * Manual create. Dedupes by emailNorm then nameNorm — 409 on conflict.
     */
    public FamilyMember createManual(String ownerUserId, String displayName, String email) {
        if (ownerUserId == null || ownerUserId.isBlank()) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Missing user");
        }
        String name = displayName != null ? displayName.trim() : "";
        if (name.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "displayName is required");
        }
        String emailNorm = normEmail(email);
        String nameNorm = normName(name);
        if (emailNorm != null
                && familyMemberRepository.findFirstByOwnerUserIdAndEmailNorm(ownerUserId, emailNorm).isPresent()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Family member with this email already exists");
        }
        if (familyMemberRepository.findFirstByOwnerUserIdAndNameNorm(ownerUserId, nameNorm).isPresent()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Family member with this name already exists");
        }
        enforceMaxFamilyMembers(ownerUserId);
        FamilyMemberDocument created = FamilyMemberDocument.builder()
                .id(UUID.randomUUID().toString())
                .ownerUserId(ownerUserId)
                .displayName(name)
                .email(email != null && !email.isBlank() ? email.trim() : null)
                .emailNorm(emailNorm)
                .nameNorm(nameNorm)
                .source("MANUAL")
                .createdAt(LocalDateTime.now())
                .build();
        return toModel(familyMemberRepository.savePreservingBsonId(created), 0);
    }

    public FamilyMember updateOwned(String familyMemberId, String ownerUserId, String displayName, String email) {
        FamilyMemberDocument existing = requireOwned(familyMemberId, ownerUserId);
        boolean dirty = false;
        if (displayName != null) {
            String name = displayName.trim();
            if (name.isBlank()) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "displayName cannot be blank");
            }
            String nameNorm = normName(name);
            familyMemberRepository.findFirstByOwnerUserIdAndNameNorm(ownerUserId, nameNorm)
                    .ifPresent(other -> {
                        if (!Objects.equals(other.getId(), existing.getId())) {
                            throw new ResponseStatusException(HttpStatus.CONFLICT,
                                    "Family member with this name already exists");
                        }
                    });
            existing.setDisplayName(name);
            existing.setNameNorm(nameNorm);
            dirty = true;
        }
        if (email != null) {
            String trimmed = email.trim();
            String emailNorm = trimmed.isEmpty() ? null : normEmail(trimmed);
            if (emailNorm != null) {
                familyMemberRepository.findFirstByOwnerUserIdAndEmailNorm(ownerUserId, emailNorm)
                        .ifPresent(other -> {
                            if (!Objects.equals(other.getId(), existing.getId())) {
                                throw new ResponseStatusException(HttpStatus.CONFLICT,
                                        "Family member with this email already exists");
                            }
                        });
            }
            existing.setEmail(trimmed.isEmpty() ? null : trimmed);
            existing.setEmailNorm(emailNorm);
            dirty = true;
        }
        if (!dirty) {
            return toModel(existing, countDematAccounts(ownerUserId, existing.getId()));
        }
        return toModel(familyMemberRepository.savePreservingBsonId(existing),
                countDematAccounts(ownerUserId, existing.getId()));
    }

    private FamilyMemberDocument requireOwned(String familyMemberId, String ownerUserId) {
        if (ownerUserId == null || ownerUserId.isBlank()) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Missing user");
        }
        if (familyMemberId == null || familyMemberId.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid familyMemberId");
        }
        familyMemberRepository.consolidateObjectIdStringTwins(ownerUserId);
        return familyMemberRepository
                .findOwnedById(familyMemberId, ownerUserId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Family member not found"));
    }

    /** Non-MF demat rows from that persona's CAS directory. */
    public int countDematAccounts(String ownerUserId, String familyMemberId) {
        if (ownerUserId == null || familyMemberId == null || familyMemberId.isBlank()) {
            return 0;
        }
        CasImportDocument cas = casImportRepository
                .findFirstByOwnerAndFamilyMemberIdOrderByAsOfDateDescImportedAtDesc(ownerUserId, familyMemberId)
                .orElse(null);
        if (cas == null || cas.getAccountDirectory() == null) {
            return 0;
        }
        int n = 0;
        for (DematAccountModel a : cas.getAccountDirectory()) {
            if (a == null) {
                continue;
            }
            String type = a.getAccountType() == null ? "" : a.getAccountType().trim();
            if (!"mf".equalsIgnoreCase(type)) {
                n++;
            }
        }
        return n;
    }

    /**
     * Find-or-create when identityMatch is FAMILY. Match order: email → PAN → name.
     * Returns null for SELF / UNKNOWN / blank identity.
     */
    public FamilyMember resolveForImport(
            String ownerUserId,
            String identityMatch,
            String holderName,
            String holderEmail,
            String source) {
        return resolveForImport(ownerUserId, identityMatch, holderName, holderEmail, null, source);
    }

    public FamilyMember resolveForImport(
            String ownerUserId,
            String identityMatch,
            String holderName,
            String holderEmail,
            String holderPan,
            String source) {
        if (ownerUserId == null || ownerUserId.isBlank()) {
            return null;
        }
        if (!"FAMILY".equalsIgnoreCase(nullToEmpty(identityMatch))) {
            return null;
        }
        familyMemberRepository.consolidateObjectIdStringTwins(ownerUserId);
        String emailNorm = normEmail(holderEmail);
        String panNorm = normPan(holderPan);
        String nameNorm = normName(holderName);
        if (emailNorm == null && panNorm == null && nameNorm == null) {
            return null;
        }

        FamilyMemberDocument existing = null;
        if (emailNorm != null) {
            existing = familyMemberRepository
                    .findFirstByOwnerUserIdAndEmailNorm(ownerUserId, emailNorm)
                    .orElse(null);
        }
        if (existing == null && panNorm != null) {
            existing = familyMemberRepository
                    .findFirstByOwnerUserIdAndPanNorm(ownerUserId, panNorm)
                    .orElse(null);
        }
        if (existing == null && nameNorm != null) {
            existing = familyMemberRepository
                    .findFirstByOwnerUserIdAndNameNorm(ownerUserId, nameNorm)
                    .orElse(null);
        }
        if (existing != null) {
            boolean dirty = false;
            if (holderName != null && !holderName.isBlank()
                    && isGenericOrBlankDisplayName(existing.getDisplayName())) {
                existing.setDisplayName(holderName.trim());
                existing.setNameNorm(nameNorm);
                dirty = true;
            }
            if (holderEmail != null && !holderEmail.isBlank()
                    && (existing.getEmail() == null || existing.getEmail().isBlank()
                            || looksTruncatedEmail(existing.getEmail()))) {
                existing.setEmail(holderEmail.trim());
                existing.setEmailNorm(emailNorm);
                dirty = true;
            }
            if (panNorm != null
                    && (existing.getPanNorm() == null || existing.getPanNorm().isBlank())) {
                existing.setPan(panNorm);
                existing.setPanNorm(panNorm);
                dirty = true;
            }
            // Always savePreservingBsonId to normalize ObjectId → String if needed.
            existing = familyMemberRepository.savePreservingBsonId(existing);
            return toModel(existing, null);
        }

        enforceMaxFamilyMembers(ownerUserId);
        FamilyMemberDocument created = FamilyMemberDocument.builder()
                .id(UUID.randomUUID().toString())
                .ownerUserId(ownerUserId)
                .displayName(holderName != null && !holderName.isBlank() ? holderName.trim() : "Family member")
                .email(holderEmail != null && !holderEmail.isBlank() ? holderEmail.trim() : null)
                .emailNorm(emailNorm)
                .nameNorm(nameNorm)
                .pan(panNorm)
                .panNorm(panNorm)
                .source(source != null ? source : "CAS")
                .createdAt(LocalDateTime.now())
                .build();
        return toModel(familyMemberRepository.savePreservingBsonId(created), null);
    }

    private void enforceMaxFamilyMembers(String ownerUserId) {
        long count = familyMemberRepository.countByOwnerUserId(ownerUserId);
        if (count >= MAX_FAMILY_MEMBERS) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Maximum of " + MAX_FAMILY_MEMBERS + " family members allowed");
        }
    }

    private static boolean prefer(FamilyMemberDocument candidate, FamilyMemberDocument current) {
        if (isGenericOrBlankDisplayName(current.getDisplayName())
                && !isGenericOrBlankDisplayName(candidate.getDisplayName())) {
            return true;
        }
        return false;
    }

    private FamilyMember toModel(FamilyMemberDocument doc, Integer portfolioCount) {
        if (doc == null) {
            return null;
        }
        return FamilyMember.builder()
                .id(doc.getId())
                .ownerUserId(doc.getOwnerUserId())
                .displayName(doc.getDisplayName())
                .email(doc.getEmail())
                .pan(doc.getPan())
                .source(doc.getSource())
                .createdAt(doc.getCreatedAt())
                .portfolioCount(portfolioCount)
                .build();
    }

    private static String normEmail(String email) {
        if (email == null || email.isBlank()) {
            return null;
        }
        return email.trim().toLowerCase(Locale.ROOT);
    }

    /** Uppercase 10-char Indian PAN or null. */
    public static String normPan(String pan) {
        if (pan == null || pan.isBlank()) {
            return null;
        }
        String p = pan.trim().toUpperCase(Locale.ROOT);
        if (p.length() != 10) {
            return null;
        }
        return p;
    }

    private static String normName(String name) {
        if (name == null || name.isBlank()) {
            return null;
        }
        return name.trim().toLowerCase(Locale.ROOT).replaceAll("\\s+", " ");
    }

    private static String nullToEmpty(String s) {
        return s == null ? "" : s;
    }

    /** Placeholder used when CAS/NPS had email but no holder name. */
    public static boolean isGenericOrBlankDisplayName(String name) {
        if (name == null || name.isBlank()) {
            return true;
        }
        String n = name.trim();
        return "Family member".equalsIgnoreCase(n) || "Family Member".equals(n);
    }

    /** CDSL sometimes splits email across lines → {@code user@gmail.} without TLD. */
    public static boolean looksTruncatedEmail(String email) {
        if (email == null || email.isBlank()) {
            return true;
        }
        String e = email.trim();
        int at = e.indexOf('@');
        if (at < 1 || at == e.length() - 1) {
            return true;
        }
        String domain = e.substring(at + 1);
        return !domain.contains(".") || domain.endsWith(".");
    }
}
