package com.portfolio.service.family;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;

/**
 * Soft-family stamp rules: primary = blank familyMemberId; live = id in family_members;
 * orphan = non-blank id not in the live set.
 */
public final class FamilyPersonaScope {

    private FamilyPersonaScope() {}

    public static boolean isPrimary(String familyMemberId) {
        return familyMemberId == null || familyMemberId.isBlank();
    }

    public static boolean isLiveStamp(String familyMemberId, Set<String> liveMemberIds) {
        if (isPrimary(familyMemberId) || liveMemberIds == null || liveMemberIds.isEmpty()) {
            return false;
        }
        return liveMemberIds.contains(familyMemberId.trim());
    }

    public static boolean isOrphanStamp(String familyMemberId, Set<String> liveMemberIds) {
        if (isPrimary(familyMemberId)) {
            return false;
        }
        if (liveMemberIds == null || liveMemberIds.isEmpty()) {
            return true;
        }
        return !liveMemberIds.contains(familyMemberId.trim());
    }

    public static Set<String> liveIds(Collection<String> memberIds) {
        if (memberIds == null || memberIds.isEmpty()) {
            return Set.of();
        }
        Set<String> out = new LinkedHashSet<>();
        for (String id : memberIds) {
            if (id != null && !id.isBlank()) {
                out.add(id.trim());
            }
        }
        return Collections.unmodifiableSet(out);
    }

    /** Same normalization as {@link FamilyMemberService} nameNorm. */
    public static String normName(String name) {
        if (name == null || name.isBlank()) {
            return null;
        }
        return name.trim().toLowerCase(Locale.ROOT).replaceAll("\\s+", " ");
    }

    public static boolean nameNormEquals(String leftName, String rightNorm) {
        if (rightNorm == null || rightNorm.isBlank()) {
            return false;
        }
        return Objects.equals(normName(leftName), rightNorm);
    }
}