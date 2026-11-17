package com.portfolio.analytics.intelligence;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import com.am.common.amcommondata.document.family.FamilyMemberDocument;
import com.am.common.amcommondata.model.PortfolioModelV1;
import com.am.common.amcommondata.model.family.HoldingsPersona;
import com.am.common.amcommondata.repository.family.FamilyMemberRepository;
import com.am.common.amcommondata.service.PortfolioService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Loads the authenticated owner's portfolios and merges BROKER books for All-Portfolios analytics.
 * Short L1 TTL avoids re-reading Mongo on every Overview /all/* call.
 */
@Service
@RequiredArgsConstructor
public class AggregatePortfolioLoader {

    static final long MERGE_TTL_MS = 45_000L;

    private final PortfolioService portfolioService;
    private final AggregatePortfolioFactory aggregatePortfolioFactory;
    private final FamilyMemberRepository familyMemberRepository;

    private final ConcurrentHashMap<String, CachedMerge> mergeCache = new ConcurrentHashMap<>();

    public PortfolioModelV1 loadMerged(String userId) {
        return loadMerged(userId, null);
    }

    public PortfolioModelV1 loadMerged(String userId, String persona) {
        if (userId == null || userId.isBlank()) {
            throw new IllegalArgumentException("userId required");
        }
        String owner = userId.trim();
        String key = owner + "|" + (HoldingsPersona.isHousehold(persona) ? HoldingsPersona.HOUSEHOLD : persona.trim());
        long now = System.currentTimeMillis();
        CachedMerge hit = mergeCache.get(key);
        if (hit != null && hit.expiresAtMs() > now) {
            return hit.model();
        }
        List<PortfolioModelV1> books = portfolioService.getPortfoliosByUserId(owner);
        Set<String> liveMemberIds = HoldingsPersona.isHousehold(persona) ? liveMemberIds(owner) : null;
        List<PortfolioModelV1> scoped = books == null ? List.of() : books.stream()
                .filter(p -> p != null && HoldingsPersona.matches(p.getFamilyMemberId(), persona, liveMemberIds))
                .toList();
        PortfolioModelV1 merged = aggregatePortfolioFactory.mergeBrokerBooks(owner, scoped);
        mergeCache.put(key, new CachedMerge(merged, now + MERGE_TTL_MS));
        return merged;
    }

    /** Per-broker book values for All-Portfolios X-Ray By Portfolio tab. */
    public Map<String, Double> loadBrokerBookValues(String userId) {
        return loadBrokerBookValues(userId, null);
    }

    public Map<String, Double> loadBrokerBookValues(String userId, String persona) {
        if (userId == null || userId.isBlank()) {
            throw new IllegalArgumentException("userId required");
        }
        List<PortfolioModelV1> books = portfolioService.getPortfoliosByUserId(userId.trim());
        Set<String> liveMemberIds = HoldingsPersona.isHousehold(persona) ? liveMemberIds(userId.trim()) : null;
        List<PortfolioModelV1> scoped = books == null ? List.of() : books.stream()
                .filter(p -> p != null && HoldingsPersona.matches(p.getFamilyMemberId(), persona, liveMemberIds))
                .toList();
        return AggregatePortfolioFactory.brokerBookValues(scoped);
    }

    /** Drop L1 merge after holdings writes so All-Portfolios reflects new books. */
    public void evict(String userId) {
        if (userId == null || userId.isBlank()) {
            return;
        }
        String prefix = userId.trim();
        mergeCache.keySet().removeIf(k -> k.equals(prefix) || k.startsWith(prefix + "|"));
    }

    private Set<String> liveMemberIds(String ownerUserId) {
        if (familyMemberRepository == null) {
            return Set.of();
        }
        List<FamilyMemberDocument> docs =
                familyMemberRepository.findByOwnerUserIdOrderByDisplayNameAsc(ownerUserId);
        List<String> ids = new ArrayList<>();
        if (docs != null) {
            for (FamilyMemberDocument doc : docs) {
                if (doc != null && doc.getId() != null && !doc.getId().isBlank()) {
                    ids.add(doc.getId().trim());
                }
            }
        }
        return Collections.unmodifiableSet(new LinkedHashSet<>(ids));
    }

    private record CachedMerge(PortfolioModelV1 model, long expiresAtMs) {}
}
