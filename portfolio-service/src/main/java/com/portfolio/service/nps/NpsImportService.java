package com.portfolio.service.nps;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.springframework.stereotype.Service;

import com.am.common.amcommondata.document.nps.NpsImportDocument;
import com.am.common.amcommondata.model.PortfolioModelV1;
import com.am.common.amcommondata.model.asset.AssetModel;
import com.am.common.amcommondata.model.enums.BrokerType;
import com.am.common.amcommondata.model.nps.NpsDocumentMeta;
import com.am.common.amcommondata.model.nps.NpsSchemeLine;
import com.am.common.amcommondata.model.family.FamilyMember;
import com.am.common.amcommondata.repository.nps.NpsImportRepository;
import com.am.common.amcommondata.service.PortfolioService;
import com.portfolio.model.events.NpsDocumentEvent;
import com.portfolio.service.family.FamilyMemberService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class NpsImportService {

    private final NpsImportRepository npsImportRepository;
    private final PortfolioService portfolioService;
    private final FamilyMemberService familyMemberService;

    public NpsImportDocument upsertFromEvent(NpsDocumentEvent event) {
        if (event == null || event.getUserId() == null || event.getUserId().isBlank()) {
            throw new IllegalArgumentException("NPS_DOCUMENT requires userId");
        }
        String owner = event.getUserId();
        NpsImportDocument existing = npsImportRepository
                .findFirstByOwnerOrderByAsOfDateDescImportedAtDesc(owner)
                .orElse(null);

        String holderName = event.getHolderName();
        String holderEmail = event.getHolderEmail();
        if (event.getSubscriber() != null) {
            if ((holderName == null || holderName.isBlank()) && event.getSubscriber().get("name") != null) {
                holderName = String.valueOf(event.getSubscriber().get("name"));
            }
            if ((holderEmail == null || holderEmail.isBlank()) && event.getSubscriber().get("email") != null) {
                holderEmail = String.valueOf(event.getSubscriber().get("email"));
            }
        }

        FamilyMember member = familyMemberService.resolveForImport(
                owner, event.getIdentityMatch(), holderName, holderEmail, "NPS");

        NpsImportDocument doc = existing != null ? existing : NpsImportDocument.builder().build();
        doc.setOwner(owner);
        doc.setDocumentSource(event.getDocumentSource() != null ? event.getDocumentSource() : "NPS_STATEMENT");
        doc.setAsOfDate(event.getAsOfDate() != null ? event.getAsOfDate() : event.getStatementTo());
        doc.setStatementFrom(event.getStatementFrom());
        doc.setStatementTo(event.getStatementTo());
        doc.setImportedAt(LocalDateTime.now());
        doc.setSubscriber(event.getSubscriber());
        doc.setTiers(event.getTiers());
        doc.setValidation(event.getValidation());
        doc.setIdentityMatch(event.getIdentityMatch());
        doc.setHolderName(holderName);
        doc.setHolderEmail(holderEmail);
        if (member != null) {
            doc.setFamilyMemberId(member.getId());
            doc.setFamilyMemberName(member.getDisplayName());
        } else {
            doc.setFamilyMemberId(null);
            doc.setFamilyMemberName(null);
        }
        if (event.getSubscriber() != null && event.getSubscriber().get("pran") != null) {
            doc.setPran(String.valueOf(event.getSubscriber().get("pran")));
        }

        NpsImportDocument saved = npsImportRepository.save(doc);
        log.info("Upserted NPS import for owner={} pran={} asOf={} familyMemberId={}",
                owner, saved.getPran(), saved.getAsOfDate(), saved.getFamilyMemberId());
        return saved;
    }

    public Optional<NpsImportDocument> latest(String userId) {
        return npsImportRepository.findFirstByOwnerOrderByAsOfDateDescImportedAtDesc(userId);
    }

    /** One latest NPS meta per persona (null familyMemberId = primary). */
    public List<NpsDocumentMeta> listMetasForOwner(String userId) {
        if (userId == null || userId.isBlank()) {
            return Collections.emptyList();
        }
        List<NpsImportDocument> all =
                npsImportRepository.findByOwnerOrderByAsOfDateDescImportedAtDesc(userId);
        if (all == null || all.isEmpty()) {
            return Collections.emptyList();
        }
        Map<String, NpsImportDocument> latestByPersona = new java.util.LinkedHashMap<>();
        for (NpsImportDocument doc : all) {
            if (doc == null) {
                continue;
            }
            String key = doc.getFamilyMemberId() == null || doc.getFamilyMemberId().isBlank()
                    ? ""
                    : doc.getFamilyMemberId().trim();
            latestByPersona.putIfAbsent(key, doc);
        }
        List<NpsDocumentMeta> out = new ArrayList<>();
        for (NpsImportDocument doc : latestByPersona.values()) {
            toMeta(doc).ifPresent(out::add);
        }
        return out;
    }

    public Optional<NpsDocumentMeta> latestMeta(String userId) {
        return latest(userId).flatMap(this::toMeta);
    }

    private Optional<NpsDocumentMeta> toMeta(NpsImportDocument doc) {
        if (doc == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(mapDocToMeta(doc));
    }

    private NpsDocumentMeta mapDocToMeta(NpsImportDocument doc) {
        Double t1 = null;
        Double t2 = null;
        if (doc.getTiers() != null) {
            for (Map<String, Object> tier : doc.getTiers()) {
                if (tier == null) {
                    continue;
                }
                Object tierId = tier.get("tier");
                Object value = null;
                Object summary = tier.get("investmentSummary");
                if (summary instanceof Map<?, ?> sm) {
                    value = sm.get("holdingsValue");
                }
                if (value == null) {
                    value = tier.get("closingValue");
                }
                Double v = toDouble(value);
                if ("I".equals(String.valueOf(tierId)) || "1".equals(String.valueOf(tierId))) {
                    t1 = v;
                } else if ("II".equals(String.valueOf(tierId)) || "2".equals(String.valueOf(tierId))) {
                    t2 = v;
                }
            }
        }
        Double total = null;
        if (t1 != null || t2 != null) {
            total = (t1 != null ? t1 : 0.0) + (t2 != null ? t2 : 0.0);
        }
        String name = null;
        String email = null;
        String mobile = null;
        String nomineeDetails = null;
        String registrationDate = null;
        if (doc.getSubscriber() != null) {
            if (doc.getSubscriber().get("name") != null) {
                name = String.valueOf(doc.getSubscriber().get("name"));
            }
            if (doc.getSubscriber().get("email") != null) {
                email = String.valueOf(doc.getSubscriber().get("email"));
            }
            if (doc.getSubscriber().get("mobile") != null) {
                mobile = String.valueOf(doc.getSubscriber().get("mobile"));
            }
            if (doc.getSubscriber().get("registrationDate") != null) {
                registrationDate = String.valueOf(doc.getSubscriber().get("registrationDate")).trim();
                if (registrationDate.isEmpty()) {
                    registrationDate = null;
                }
            }
            nomineeDetails = formatNominee(doc.getSubscriber().get("nominee"));
        }
        return NpsDocumentMeta.builder()
                .documentSource(doc.getDocumentSource())
                .asOfDate(doc.getAsOfDate())
                .statementFrom(doc.getStatementFrom())
                .statementTo(doc.getStatementTo())
                .importedAt(doc.getImportedAt())
                .pran(doc.getPran())
                .subscriberName(name)
                .subscriberEmail(email)
                .subscriberMobile(mobile)
                .nomineeDetails(nomineeDetails)
                .registrationDate(registrationDate)
                .tier1Value(t1)
                .tier2Value(t2)
                .totalValue(total)
                .validation(doc.getValidation())
                .familyMemberId(doc.getFamilyMemberId())
                .familyMemberName(doc.getFamilyMemberName())
                .build();
    }

    static String formatNominee(Object nominee) {
        if (nominee == null) {
            return null;
        }
        if (nominee instanceof Map<?, ?> map) {
            Object n = map.get("name");
            if (n == null) {
                return null;
            }
            String name = String.valueOf(n).trim();
            if (name.isEmpty()) {
                return null;
            }
            Object pct = map.get("percentage");
            if (pct == null) {
                return name;
            }
            String pctStr;
            if (pct instanceof Number num) {
                double d = num.doubleValue();
                pctStr = d == Math.rint(d) ? String.valueOf((long) d) : String.valueOf(d);
            } else {
                pctStr = String.valueOf(pct).trim();
            }
            return pctStr.isEmpty() ? name : name + " (" + pctStr + "%)";
        }
        String s = String.valueOf(nominee).trim();
        return s.isEmpty() ? null : s;
    }

    public List<NpsSchemeLine> schemesForUser(String userId) {
        List<PortfolioModelV1> portfolios = portfolioService.getPortfoliosByUserId(userId);
        if (portfolios == null) {
            return Collections.emptyList();
        }
        List<NpsSchemeLine> out = new ArrayList<>();
        for (PortfolioModelV1 p : portfolios) {
            if (p == null || p.getBrokerType() != BrokerType.NPS) {
                continue;
            }
            if (p.getNpsModels() == null) {
                continue;
            }
            for (AssetModel a : p.getNpsModels()) {
                if (a == null) {
                    continue;
                }
                Double stmtNav = a.getStatementNav() != null ? a.getStatementNav() : a.getAvgBuyingPrice();
                Double liveNav = a.getLiveNav() != null ? a.getLiveNav() : a.getCurrentPrice();
                Double units = a.getQuantity();
                Double stmtValue = units != null && stmtNav != null ? units * stmtNav : null;
                Double liveValue = a.getCurrentValue();
                if (liveValue == null && units != null && liveNav != null) {
                    liveValue = units * liveNav;
                }
                Double gain = null;
                if (liveValue != null && stmtValue != null) {
                    gain = liveValue - stmtValue;
                }
                String pran = null;
                String tier = null;
                if (a instanceof com.am.common.amcommondata.model.asset.pension.NPSModel nps) {
                    pran = nps.getPran();
                    tier = nps.getTier();
                }
                out.add(NpsSchemeLine.builder()
                        .isin(a.getIsin())
                        .schemeCode(a.getSchemeCode())
                        .name(a.getName())
                        .tier(tier)
                        .schemeClass(a.getSchemeClass())
                        .schemeOption(a.getSchemeOption())
                        .units(units)
                        .statementNav(stmtNav)
                        .liveNav(liveNav)
                        .statementValue(stmtValue)
                        .liveValue(liveValue)
                        .gainVsStatement(gain)
                        .isinResolveStatus(a.getIsinResolveStatus())
                        .pran(pran)
                        .build());
            }
        }
        return out;
    }

    public List<NpsSchemeLine> schemesForPortfolio(String userId, String portfolioId) {
        // Schemes are on the unique NPS broker book; portfolioId asserts ownership upstream.
        return schemesForUser(userId);
    }

    private static Double toDouble(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Number) {
            return ((Number) value).doubleValue();
        }
        try {
            return Double.parseDouble(String.valueOf(value).replace(",", ""));
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
