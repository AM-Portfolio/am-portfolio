package com.portfolio.service.family;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Set;

import org.junit.jupiter.api.Test;

class FamilyPersonaScopeTest {

    @Test
    void primaryAndOrphanRules() {
        Set<String> live = Set.of("fm1");
        assertTrue(FamilyPersonaScope.isPrimary(null));
        assertTrue(FamilyPersonaScope.isPrimary(" "));
        assertTrue(FamilyPersonaScope.isLiveStamp("fm1", live));
        assertFalse(FamilyPersonaScope.isOrphanStamp("fm1", live));
        assertTrue(FamilyPersonaScope.isOrphanStamp("stale", live));
        assertTrue(FamilyPersonaScope.isOrphanStamp("stale", Set.of()));
        assertFalse(FamilyPersonaScope.isOrphanStamp(null, live));
    }

    @Test
    void nameNormMatchesDisplayVariants() {
        assertTrue(FamilyPersonaScope.nameNormEquals("PRIYANSHU   KUMAR", "priyanshu kumar"));
        assertFalse(FamilyPersonaScope.nameNormEquals("Someone Else", "priyanshu kumar"));
    }
}
