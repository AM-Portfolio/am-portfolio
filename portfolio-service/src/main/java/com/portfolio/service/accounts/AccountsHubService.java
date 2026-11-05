package com.portfolio.service.accounts;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.stream.Stream;

import org.springframework.stereotype.Service;

import com.am.common.amcommondata.model.PortfolioModelV1;
import com.am.common.amcommondata.model.accounts.AccountsHubModel;
import com.am.common.amcommondata.model.accounts.AccountsHubModel.AccountsIdentity;
import com.am.common.amcommondata.model.accounts.AccountsHubModel.AccountsPortfolioRow;
import com.am.common.amcommondata.model.accounts.AccountsHubModel.AccountsStats;
import com.am.common.amcommondata.model.accounts.AccountsHubModel.FamilyMemberSummary;
import com.am.common.amcommondata.model.asset.AssetModel;
import com.am.common.amcommondata.model.cas.CasDocumentMeta;
import com.am.common.amcommondata.model.demat.DematAccountModel;
import com.am.common.amcommondata.model.enums.BrokerType;
import com.am.common.amcommondata.model.enums.PortfolioKind;
import com.am.common.amcommondata.model.family.FamilyMember;
import com.am.common.amcommondata.model.nps.NpsDocumentMeta;
import com.am.common.amcommondata.service.PortfolioService;
import com.portfolio.service.accounts.OverallXirrCalculator.Cashflow;
import com.portfolio.service.cas.CasImportService;
import com.portfolio.service.family.FamilyMemberService;
import com.portfolio.service.nps.NpsImportService;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class AccountsHubService {

    private final CasImportService casImportService;
    private final NpsImportService npsImportService;
    private final PortfolioService portfolioService;
    private final FamilyMemberService familyMemberService;

    public AccountsHubModel hubForUser(String userId) {
        // All personas stamped with familyMemberId (null = primary).
        List<DematAccountModel> demat = new ArrayList<>(
                casImportService.accountsForOwnerAllPersonas(userId));
        List<NpsDocumentMeta> npsAccounts = npsImportService.listMetasForOwner(userId);
        NpsDocumentMeta nps = npsAccounts.stream()
                .filter(m -> m != null && (m.getFamilyMemberId() == null || m.getFamilyMemberId().isBlank()))
                .findFirst()
                .orElse(npsImportService.latestMeta(userId).orElse(null));
        CasDocumentMeta cas = casImportService.latestMeta(userId).orElse(null);

        List<PortfolioModelV1> portfolios = portfolioService.getPortfoliosByUserId(userId);
        if (portfolios == null) {
            portfolios = List.of();
        }

        List<AccountsPortfolioRow> rows = new ArrayList<>();
        List<PortfolioModelV1> xirrBooks = new ArrayList<>();
        for (PortfolioModelV1 p : portfolios) {
            if (p == null || isCdslAggregator(p)) {
                continue;
            }
            rows.add(toPortfolioRow(p));
            if (!isPaperPortfolio(p)
                    && !Boolean.TRUE.equals(p.getIsDummy())
                    && p.getBrokerType() != BrokerType.NPS) {
                xirrBooks.add(p);
            }
        }

        // Hub-level stats default to primary persona (null familyMemberId).
        List<DematAccountModel> primaryDemat = demat.stream()
                .filter(a -> a != null && (a.getFamilyMemberId() == null || a.getFamilyMemberId().isBlank()))
                .toList();
        int activeDemat = (int) primaryDemat.stream()
                .filter(a -> "demat".equalsIgnoreCase(nullToEmpty(a.getAccountType())))
                .filter(a -> Boolean.TRUE.equals(a.getActive())
                        || (a.getStatus() != null && a.getStatus().toLowerCase(Locale.ROOT).contains("active")))
                .count();
        int dematCount = (int) primaryDemat.stream()
                .filter(a -> "demat".equalsIgnoreCase(nullToEmpty(a.getAccountType())))
                .count();
        int mfCount = (int) primaryDemat.stream()
                .filter(a -> "mf".equalsIgnoreCase(nullToEmpty(a.getAccountType())))
                .count();
        int brokerCount = (int) rows.stream()
                .filter(r -> r.getFamilyMemberId() == null || r.getFamilyMemberId().isBlank())
                .filter(r -> !isPaperLike(r))
                .filter(r -> !isNpsRow(r))
                .filter(r -> r.getCurrent() != null && r.getCurrent() > 0)
                .count();

        List<PortfolioModelV1> primaryXirrBooks = xirrBooks.stream()
                .filter(p -> p.getFamilyMemberId() == null || p.getFamilyMemberId().isBlank())
                .toList();

        List<FamilyMember> members = familyMemberService.listForOwner(userId);
        List<FamilyMemberSummary> familySummaries = members.stream()
                .map(m -> {
                    String mid = m.getId();
                    List<PortfolioModelV1> memberBooks = xirrBooks.stream()
                            .filter(p -> mid != null && mid.equals(p.getFamilyMemberId()))
                            .toList();
                    return FamilyMemberSummary.builder()
                            .id(mid)
                            .name(m.getDisplayName())
                            .email(m.getEmail())
                            .source(m.getSource())
                            // portfolioCount = non-MF demat accounts for that persona's CAS.
                            .portfolioCount(countDematForMember(demat, mid))
                            .totalValue(round2(sumValueForMemberId(demat, rows, npsAccounts, mid)))
                            .xirrPct(computeOverallXirr(memberBooks))
                            .build();
                })
                .toList();

        double primaryTotal = sumPersonaValue(demat, rows, npsAccounts, true);
        double familyTotal = sumPersonaValue(demat, rows, npsAccounts, false);
        double household = primaryTotal + familyTotal;
        Double familySharePct = household > 0 ? round2((familyTotal / household) * 100.0) : null;

        return AccountsHubModel.builder()
                .identity(buildIdentity(userId, nps, primaryDemat))
                .stats(AccountsStats.builder()
                        .activeDematCount(activeDemat)
                        .dematAccountCount(dematCount)
                        .mfFolioCount(mfCount)
                        .npsLinked(nps != null && nps.getPran() != null && !nps.getPran().isBlank())
                        .brokerPortfolioCount(brokerCount)
                        .overallXirrPct(computeOverallXirr(primaryXirrBooks))
                        .householdXirrPct(computeOverallXirr(xirrBooks))
                        .primaryTotalValue(round2(primaryTotal))
                        .familyTotalValue(round2(familyTotal))
                        .householdTotalValue(round2(household))
                        .familySharePct(familySharePct)
                        .build())
                .portfolios(rows)
                .dematAccounts(demat)
                .nps(nps)
                .npsAccounts(npsAccounts)
                .cas(cas)
                .familyMembers(familySummaries)
                .build();
    }

    /**
     * @param primaryOnly true = null familyMemberId only; false = any stamped family id
     */
    static double sumPersonaValue(
            List<DematAccountModel> demat,
            List<AccountsPortfolioRow> rows,
            List<NpsDocumentMeta> npsAccounts,
            boolean primaryOnly) {
        double sum = 0.0;
        if (demat != null) {
            for (DematAccountModel a : demat) {
                if (a == null || !personaMatches(a.getFamilyMemberId(), primaryOnly)) {
                    continue;
                }
                if (a.getHoldingValue() != null && a.getHoldingValue() > 0) {
                    sum += a.getHoldingValue();
                }
            }
        }
        if (rows != null) {
            for (AccountsPortfolioRow r : rows) {
                if (r == null || isPaperLike(r) || isNpsRow(r)) {
                    continue;
                }
                if (!personaMatches(r.getFamilyMemberId(), primaryOnly)) {
                    continue;
                }
                if (r.getCurrent() != null && r.getCurrent() > 0) {
                    sum += r.getCurrent();
                }
            }
        }
        if (npsAccounts != null) {
            for (NpsDocumentMeta m : npsAccounts) {
                if (m == null || !personaMatches(m.getFamilyMemberId(), primaryOnly)) {
                    continue;
                }
                if (m.getTotalValue() != null && m.getTotalValue() > 0) {
                    sum += m.getTotalValue();
                }
            }
        }
        return sum;
    }

    private static boolean personaMatches(String familyMemberId, boolean primaryOnly) {
        boolean isPrimary = familyMemberId == null || familyMemberId.isBlank();
        return primaryOnly ? isPrimary : !isPrimary;
    }

    private static Double round2(double v) {
        return Math.round(v * 100.0) / 100.0;
    }

    static int countDematForMember(List<DematAccountModel> demat, String familyMemberId) {
        if (familyMemberId == null || familyMemberId.isBlank() || demat == null) {
            return 0;
        }
        return (int) demat.stream()
                .filter(a -> a != null && familyMemberId.equals(a.getFamilyMemberId()))
                .filter(a -> !"mf".equalsIgnoreCase(nullToEmpty(a.getAccountType())))
                .count();
    }

    /** Sum demat + broker rows + NPS for one soft-family member id. */
    static double sumValueForMemberId(
            List<DematAccountModel> demat,
            List<AccountsPortfolioRow> rows,
            List<NpsDocumentMeta> npsAccounts,
            String familyMemberId) {
        if (familyMemberId == null || familyMemberId.isBlank()) {
            return 0.0;
        }
        double sum = 0.0;
        if (demat != null) {
            for (DematAccountModel a : demat) {
                if (a == null || !familyMemberId.equals(a.getFamilyMemberId())) {
                    continue;
                }
                if (a.getHoldingValue() != null && a.getHoldingValue() > 0) {
                    sum += a.getHoldingValue();
                }
            }
        }
        if (rows != null) {
            for (AccountsPortfolioRow r : rows) {
                if (r == null || isPaperLike(r) || isNpsRow(r)) {
                    continue;
                }
                if (!familyMemberId.equals(r.getFamilyMemberId())) {
                    continue;
                }
                if (r.getCurrent() != null && r.getCurrent() > 0) {
                    sum += r.getCurrent();
                }
            }
        }
        if (npsAccounts != null) {
            for (NpsDocumentMeta m : npsAccounts) {
                if (m == null || !familyMemberId.equals(m.getFamilyMemberId())) {
                    continue;
                }
                if (m.getTotalValue() != null && m.getTotalValue() > 0) {
                    sum += m.getTotalValue();
                }
            }
        }
        return sum;
    }

    private static Double computeOverallXirr(List<PortfolioModelV1> books) {
        List<Cashflow> flows = new ArrayList<>();
        double terminal = 0.0;
        LocalDate today = LocalDate.now();
        for (PortfolioModelV1 p : books) {
            for (AssetModel a : assetStream(p).toList()) {
                Double cost = a.getInvestmentValue();
                if (cost == null && a.getAvgBuyingPrice() != null && a.getQuantity() != null) {
                    cost = a.getAvgBuyingPrice() * a.getQuantity();
                }
                LocalDate buyDate = OverallXirrCalculator.toDate(a.getCreatedAt());
                if (cost == null || cost <= 0 || buyDate == null) {
                    continue;
                }
                flows.add(new Cashflow(buyDate, -cost));
            }
            terminal += p.getTotalValue() != null ? p.getTotalValue() : sumCurrent(p);
        }
        if (flows.isEmpty() || terminal <= 0) {
            return null;
        }
        flows.add(new Cashflow(today, terminal));
        return OverallXirrCalculator.annualizedPct(flows);
    }

    static boolean isCdslAggregator(PortfolioModelV1 p) {
        if (p == null) {
            return false;
        }
        // Soft-family CDSL books hold MF folios + unmapped DPs for that persona — count them.
        boolean familyTagged = p.getFamilyMemberId() != null && !p.getFamilyMemberId().isBlank();
        if (p.getBrokerType() == BrokerType.CDSL) {
            return !familyTagged;
        }
        String name = nullToEmpty(p.getName()).toLowerCase(Locale.ROOT);
        if (name.contains("cdsl") || name.equals("cas") || name.contains("cas aggregator")
                || name.contains("cas mutual")) {
            return !familyTagged;
        }
        String kind = p.getPortfolioKind() != null ? p.getPortfolioKind().name() : "";
        return kind.toUpperCase(Locale.ROOT).contains("CAS") && !familyTagged;
    }

    private static boolean isPaperPortfolio(PortfolioModelV1 p) {
        if (p == null) {
            return false;
        }
        if (PortfolioKind.isPaper(p.getPortfolioKind())) {
            return true;
        }
        String name = nullToEmpty(p.getName()).trim();
        return "paper".equalsIgnoreCase(name);
    }

    private static AccountsIdentity buildIdentity(
            String userId, NpsDocumentMeta nps, List<DematAccountModel> demat) {
        String name = nps != null ? nps.getSubscriberName() : null;
        String email = nps != null ? nps.getSubscriberEmail() : null;
        String mobile = nps != null ? nps.getSubscriberMobile() : null;
        String pran = nps != null ? nps.getPran() : null;

        if (email == null || email.isBlank()) {
            email = demat.stream()
                    .map(DematAccountModel::getEmail)
                    .filter(Objects::nonNull)
                    .map(String::trim)
                    .filter(s -> !s.isEmpty())
                    .findFirst()
                    .orElse(null);
        }
        if (mobile == null || mobile.isBlank()) {
            mobile = demat.stream()
                    .map(DematAccountModel::getMobile)
                    .filter(Objects::nonNull)
                    .map(String::trim)
                    .filter(s -> !s.isEmpty())
                    .findFirst()
                    .orElse(null);
        }
        if (name == null || name.isBlank()) {
            name = demat.stream()
                    .map(DematAccountModel::getHolderName)
                    .filter(Objects::nonNull)
                    .map(String::trim)
                    .filter(s -> !s.isEmpty())
                    .findFirst()
                    .orElse(null);
        }

        return AccountsIdentity.builder()
                .userId(userId)
                .name(name)
                .email(email)
                .mobile(mobile)
                .pran(pran)
                .build();
    }

    private static AccountsPortfolioRow toPortfolioRow(PortfolioModelV1 p) {
        double current = p.getTotalValue() != null ? p.getTotalValue() : sumCurrent(p);
        Double invested = p.getInvestmentAmount();
        if (invested == null) {
            invested = sumInvested(p);
        }
        double inv = invested != null ? invested : 0.0;
        Double returnAbs = null;
        Double returnPct = null;
        if (invested != null) {
            returnAbs = current - inv;
            if (inv > 0) {
                returnPct = (returnAbs / inv) * 100.0;
            }
        }
        BrokerType broker = p.getBrokerType();
        String display = displayName(p);
        return AccountsPortfolioRow.builder()
                .portfolioId(p.getId() != null ? p.getId().toString() : null)
                .displayName(display)
                .brokerType(broker != null ? broker.name() : null)
                .portfolioKind(p.getPortfolioKind() != null ? p.getPortfolioKind().name() : null)
                .invested(invested)
                .current(current)
                .returnAbs(returnAbs)
                .returnPct(returnPct)
                .assetCount(p.getAssetCount())
                .dummy(p.getIsDummy())
                .familyMemberId(p.getFamilyMemberId())
                .familyMemberName(p.getFamilyMemberName())
                .build();
    }

    private static String displayName(PortfolioModelV1 p) {
        if (p.getName() != null && !p.getName().isBlank() && !looksLikeOpaqueId(p.getName())) {
            return p.getName();
        }
        if (p.getBrokerType() != null) {
            return brokerLabel(p.getBrokerType());
        }
        return p.getName() != null ? p.getName() : "Portfolio";
    }

    private static String brokerLabel(BrokerType broker) {
        if (broker == null) {
            return "Portfolio";
        }
        String code = broker.getCode();
        return code != null && !code.isBlank() ? code : broker.name();
    }

    private static boolean isPaperLike(AccountsPortfolioRow r) {
        if (r == null) {
            return false;
        }
        if (Boolean.TRUE.equals(r.getDummy())) {
            return true;
        }
        String name = r.getDisplayName();
        return name != null && "paper".equalsIgnoreCase(name.trim());
    }

    private static boolean isNpsRow(AccountsPortfolioRow r) {
        if (r == null) {
            return false;
        }
        String broker = r.getBrokerType();
        if (broker != null && "NPS".equalsIgnoreCase(broker.trim())) {
            return true;
        }
        String name = r.getDisplayName();
        return name != null && "NPS".equalsIgnoreCase(name.trim());
    }

    private static boolean looksLikeOpaqueId(String value) {
        if (value == null || value.isBlank()) {
            return true;
        }
        String v = value.trim();
        if (v.length() == 24 && v.matches("(?i)[0-9a-f]{24}")) {
            return true;
        }
        return v.matches("(?i)[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}");
    }

    private static double sumCurrent(PortfolioModelV1 p) {
        return assetStream(p)
                .map(AssetModel::getCurrentValue)
                .filter(Objects::nonNull)
                .mapToDouble(Double::doubleValue)
                .sum();
    }

    private static double sumInvested(PortfolioModelV1 p) {
        return assetStream(p)
                .map(a -> a.getInvestmentValue() != null ? a.getInvestmentValue() : a.getAvgBuyingPrice())
                .filter(Objects::nonNull)
                .mapToDouble(Double::doubleValue)
                .sum();
    }

    private static Stream<AssetModel> assetStream(PortfolioModelV1 p) {
        List<AssetModel> out = new ArrayList<>();
        if (p.getEquityModels() != null) {
            out.addAll(p.getEquityModels());
        }
        if (p.getMutualFunds() != null) {
            out.addAll(p.getMutualFunds());
        }
        if (p.getBonds() != null) {
            out.addAll(p.getBonds());
        }
        if (p.getCommodities() != null) {
            out.addAll(p.getCommodities());
        }
        if (p.getCash() != null) {
            out.addAll(p.getCash());
        }
        if (p.getNpsModels() != null) {
            out.addAll(p.getNpsModels());
        }
        return out.stream().filter(Objects::nonNull);
    }

    private static String nullToEmpty(String s) {
        return s == null ? "" : s;
    }
}
