package com.portfolio.service.accounts;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
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
import com.am.common.amcommondata.model.family.HoldingsPersona;
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

    private static final long XIRR_CACHE_TTL_MS = 45_000L;
    private static final ConcurrentHashMap<String, XirrCacheEntry> XIRR_CACHE =
            new ConcurrentHashMap<>();

    private final CasImportService casImportService;
    private final NpsImportService npsImportService;
    private final PortfolioService portfolioService;
    private final FamilyMemberService familyMemberService;

    /** Drop cached XIRR after CAS/NPS/portfolio mutations. */
    public static void invalidateXirrCache(String userId) {
        if (userId != null && !userId.isBlank()) {
            XIRR_CACHE.remove(userId);
        }
    }

    private record XirrCacheEntry(
            long expiresAtMs,
            Double overallXirrPct,
            Double householdXirrPct,
            Map<String, Double> memberXirrPct) {}

    /**
     * Canonical persona wealth (books + directory residual + NPS).
     * {@code you} / blank-primary, member id, or household.
     */
    public double wealthForPersona(String userId, String persona) {
        AccountsHubModel hub = hubForUser(userId);
        AccountsStats stats = hub != null ? hub.getStats() : null;
        if (stats == null) {
            return 0.0;
        }
        if (HoldingsPersona.isHousehold(persona)) {
            return stats.getHouseholdTotalValue() != null ? stats.getHouseholdTotalValue() : 0.0;
        }
        String trimmed = persona.trim();
        if (HoldingsPersona.YOU.equalsIgnoreCase(trimmed)) {
            return stats.getPrimaryTotalValue() != null ? stats.getPrimaryTotalValue() : 0.0;
        }
        if (hub.getFamilyMembers() != null) {
            for (FamilyMemberSummary m : hub.getFamilyMembers()) {
                if (m != null && trimmed.equals(m.getId()) && m.getTotalValue() != null) {
                    return m.getTotalValue();
                }
            }
        }
        return 0.0;
    }

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
            // Include NPS books: scheme lines carry cost/NAV for persona XIRR.
            if (!isPaperPortfolio(p) && !Boolean.TRUE.equals(p.getIsDummy())) {
                xirrBooks.add(p);
            }
        }
        LocalDate casAsOf = cas != null
                ? (cas.getAsOfDate() != null
                        ? cas.getAsOfDate()
                        : (cas.getStatementTo() != null ? cas.getStatementTo() : cas.getStatementFrom()))
                : null;

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
        XirrBundle xirr = resolveXirrBundle(userId, xirrBooks, primaryXirrBooks, members, casAsOf);

        List<FamilyMemberSummary> familySummaries = members.stream()
                .map(m -> {
                    String mid = m.getId();
                    return FamilyMemberSummary.builder()
                            .id(mid)
                            .name(m.getDisplayName())
                            .email(m.getEmail())
                            .source(m.getSource())
                            // portfolioCount = non-MF CAS directory rows for that persona.
                            .portfolioCount(countDematForMember(demat, mid))
                            .totalValue(round2(sumValueForMemberId(demat, rows, npsAccounts, mid)))
                            .xirrPct(mid != null ? xirr.memberXirrPct().get(mid) : null)
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
                        .overallXirrPct(xirr.overallXirrPct())
                        .householdXirrPct(xirr.householdXirrPct())
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

    private record XirrBundle(
            Double overallXirrPct,
            Double householdXirrPct,
            Map<String, Double> memberXirrPct) {}

    private XirrBundle resolveXirrBundle(
            String userId,
            List<PortfolioModelV1> xirrBooks,
            List<PortfolioModelV1> primaryXirrBooks,
            List<FamilyMember> members,
            LocalDate casAsOf) {
        XirrCacheEntry cached = XIRR_CACHE.get(userId);
        long now = System.currentTimeMillis();
        if (cached != null && cached.expiresAtMs() > now) {
            return new XirrBundle(
                    cached.overallXirrPct(),
                    cached.householdXirrPct(),
                    cached.memberXirrPct());
        }

        CompletableFuture<Double> overallFut = CompletableFuture.supplyAsync(
                () -> computeOverallXirr(primaryXirrBooks, casAsOf));
        CompletableFuture<Double> householdFut = CompletableFuture.supplyAsync(
                () -> computeOverallXirr(xirrBooks, casAsOf));

        Map<String, CompletableFuture<Double>> memberFuts = new HashMap<>();
        for (FamilyMember m : members) {
            if (m == null || m.getId() == null || m.getId().isBlank()) {
                continue;
            }
            String mid = m.getId();
            List<PortfolioModelV1> memberBooks = xirrBooks.stream()
                    .filter(p -> mid.equals(p.getFamilyMemberId()))
                    .toList();
            memberFuts.put(mid, CompletableFuture.supplyAsync(
                    () -> computeOverallXirr(memberBooks, casAsOf)));
        }

        Double overall = overallFut.join();
        Double household = householdFut.join();
        Map<String, Double> memberMap = new HashMap<>();
        for (Map.Entry<String, CompletableFuture<Double>> e : memberFuts.entrySet()) {
            Double pct = e.getValue().join();
            // Concurrent/immutable maps reject null values; omit null XIRR.
            if (pct != null) {
                memberMap.put(e.getKey(), pct);
            }
        }

        Map<String, Double> memberSnapshot = Map.copyOf(memberMap);
        XirrBundle bundle = new XirrBundle(overall, household, memberSnapshot);
        XIRR_CACHE.put(
                userId,
                new XirrCacheEntry(
                        now + XIRR_CACHE_TTL_MS,
                        overall,
                        household,
                        memberSnapshot));
        return bundle;
    }

    /**
     * @param primaryOnly true = null familyMemberId only; false = any stamped family id
     */
    static double sumPersonaValue(
            List<DematAccountModel> demat,
            List<AccountsPortfolioRow> rows,
            List<NpsDocumentMeta> npsAccounts,
            boolean primaryOnly) {
        double sum = booksPlusDirectoryResidual(demat, rows, r -> personaMatches(r.getFamilyMemberId(), primaryOnly),
                a -> personaMatches(a.getFamilyMemberId(), primaryOnly));
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

    private static Double round2(Double v) {
        return v == null ? null : round2(v.doubleValue());
    }

    static int countDematBooks(List<AccountsPortfolioRow> rows, String familyMemberId) {
        if (familyMemberId == null || familyMemberId.isBlank() || rows == null) {
            return 0;
        }
        return (int) rows.stream()
                .filter(r -> r != null && familyMemberId.equals(r.getFamilyMemberId()))
                .filter(r -> "CAS_DEMAT".equalsIgnoreCase(nullToEmpty(r.getBrokerType())))
                .filter(r -> !isMfBook(r))
                .count();
    }

    static boolean isMfBook(AccountsPortfolioRow r) {
        if (r == null) {
            return false;
        }
        if ("mf:cas".equalsIgnoreCase(nullToEmpty(r.getDematBoid()))) {
            return true;
        }
        return "mutual funds".equalsIgnoreCase(nullToEmpty(r.getDisplayName()).trim());
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
        double sum = booksPlusDirectoryResidual(
                demat,
                rows,
                r -> familyMemberId.equals(r.getFamilyMemberId()),
                a -> familyMemberId.equals(a.getFamilyMemberId()));
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

    /**
     * Positive books for the persona, lifting each book to max(book, matching CAS directory
     * line), plus directory residual for valued demats/MF that have no matching book.
     */
    static double booksPlusDirectoryResidual(
            List<DematAccountModel> demat,
            List<AccountsPortfolioRow> rows,
            java.util.function.Predicate<AccountsPortfolioRow> rowMatch,
            java.util.function.Predicate<DematAccountModel> dematMatch) {
        List<AccountsPortfolioRow> personaRows = new ArrayList<>();
        if (rows != null) {
            for (AccountsPortfolioRow r : rows) {
                if (r == null || isPaperLike(r) || isNpsRow(r) || !rowMatch.test(r)) {
                    continue;
                }
                personaRows.add(r);
            }
        }
        List<DematAccountModel> personaDemat = new ArrayList<>();
        if (demat != null) {
            for (DematAccountModel a : demat) {
                if (a == null || !dematMatch.test(a)) {
                    continue;
                }
                if (a.getHoldingValue() == null || a.getHoldingValue() <= 0) {
                    continue;
                }
                personaDemat.add(a);
            }
        }

        Set<Integer> matchedDirIdx = new HashSet<>();
        double sum = 0.0;
        for (AccountsPortfolioRow r : personaRows) {
            double book = r.getCurrent() != null && r.getCurrent() > 0 ? r.getCurrent() : 0.0;
            double dir = 0.0;
            for (int i = 0; i < personaDemat.size(); i++) {
                if (matchedDirIdx.contains(i)) {
                    continue;
                }
                DematAccountModel a = personaDemat.get(i);
                if (!directoryMatchesBook(a, r)) {
                    continue;
                }
                dir += a.getHoldingValue();
                matchedDirIdx.add(i);
            }
            sum += Math.max(book, dir);
        }
        for (int i = 0; i < personaDemat.size(); i++) {
            if (matchedDirIdx.contains(i)) {
                continue;
            }
            sum += personaDemat.get(i).getHoldingValue();
        }
        return sum;
    }

    /** Match a CAS directory row to a portfolio book (first-class broker or CAS_DEMAT BOID/MF). */
    static boolean directoryMatchesBook(DematAccountModel a, AccountsPortfolioRow r) {
        if (a == null || r == null) {
            return false;
        }
        boolean mfDir = "mf".equalsIgnoreCase(nullToEmpty(a.getAccountType()));
        if (isMfBook(r)) {
            return mfDir;
        }
        if (mfDir) {
            return false;
        }
        String broker = nullToEmpty(r.getBrokerType()).trim().toUpperCase(Locale.ROOT);
        if ("CAS_DEMAT".equals(broker)) {
            String bookBoid = blankToNull(r.getDematBoid());
            String dirBoid = blankToNull(a.getBoid());
            if (bookBoid != null && bookBoid.equalsIgnoreCase(dirBoid)) {
                return true;
            }
            String bookName = nullToEmpty(r.getDisplayName()).trim();
            String shortDp = shortDpLabel(a.getDpName());
            return !bookName.isEmpty() && bookName.equalsIgnoreCase(shortDp);
        }
        String mapped = normalizeMappedBroker(a.getMappedBroker());
        if (!mapped.isEmpty() && mapped.equals(broker)) {
            return true;
        }
        // Fallback: DP legal name contains first-class broker token.
        String dp = nullToEmpty(a.getDpName()).toUpperCase(Locale.ROOT);
        return switch (broker) {
            case "GROWW" -> dp.contains("GROWW");
            case "ANGEL_ONE" -> dp.contains("ANGEL ONE") || dp.contains("ANGELONE");
            case "ZERODHA" -> dp.contains("ZERODHA");
            case "KOTAK" -> dp.contains("KOTAK");
            case "DHAN" -> dp.contains("RAISE") || dp.contains("DHAN");
            case "UPSTOX" -> dp.contains("UPSTOX");
            case "FYERS" -> dp.contains("FYERS");
            case "HDFC" -> dp.contains("HDFC");
            case "MSTOCK" -> dp.contains("MSTOCK") || dp.contains("M STOCK");
            default -> false;
        };
    }

    private static String normalizeMappedBroker(String mapped) {
        String m = nullToEmpty(mapped).trim().toLowerCase(Locale.ROOT);
        if (m.isEmpty()) {
            return "";
        }
        return switch (m) {
            case "groww", "grow" -> "GROWW";
            case "angleone", "angelone", "angel_one" -> "ANGEL_ONE";
            case "zerodha" -> "ZERODHA";
            case "kotak" -> "KOTAK";
            case "dhan" -> "DHAN";
            case "upstox" -> "UPSTOX";
            case "fyers" -> "FYERS";
            case "hdfc", "hdfc_bank" -> "HDFC";
            case "mstock" -> "MSTOCK";
            default -> m.toUpperCase(Locale.ROOT);
        };
    }

    private static String shortDpLabel(String dpName) {
        String raw = nullToEmpty(dpName).trim();
        if (raw.isEmpty()) {
            return "";
        }
        String cleaned = raw.toUpperCase(Locale.ROOT)
                .replaceAll("\\b(SECURITIES|BROKING|BANK|CAPITAL|INVEST|TECH|ASSET|SHAREKHAN|"
                        + "SOLUTIONS|PRIVATE|LIMITED|LTD|TRADING)\\b", " ")
                .replaceAll("[^A-Z0-9\\s]", " ");
        String[] tokens = cleaned.trim().split("\\s+");
        if (tokens.length == 0 || tokens[0].isEmpty()) {
            return "";
        }
        String t = tokens[0];
        return t.charAt(0) + t.substring(1).toLowerCase(Locale.ROOT);
    }

    private static String blankToNull(String s) {
        if (s == null || s.isBlank()) {
            return null;
        }
        return s.trim();
    }

    static Double computeOverallXirr(List<PortfolioModelV1> books) {
        return computeOverallXirr(books, null);
    }

    static Double computeOverallXirr(List<PortfolioModelV1> books, LocalDate casAsOf) {
        if (books == null || books.isEmpty()) {
            return null;
        }
        List<Cashflow> flows = new ArrayList<>();
        double terminal = 0.0;
        LocalDate today = LocalDate.now();
        // Unknown buy dates: assume ~1y hold so Newton XIRR stays within calculator bounds.
        LocalDate fallbackBuy = today.minusDays(365);
        for (PortfolioModelV1 p : books) {
            if (p == null) {
                continue;
            }
            double bookTerminal = p.getTotalValue() != null ? p.getTotalValue() : sumCurrent(p);
            terminal += bookTerminal;

            int assetFlows = 0;
            for (AssetModel a : assetStream(p).toList()) {
                Double cost = resolveAssetCost(a);
                LocalDate buyDate = resolveAssetBuyDate(a, p, casAsOf, fallbackBuy);
                if (cost == null || cost <= 0 || buyDate == null) {
                    continue;
                }
                if (!buyDate.isBefore(today)) {
                    buyDate = fallbackBuy;
                }
                flows.add(new Cashflow(buyDate, -cost));
                assetFlows++;
            }

            // Portfolio-level synthetic outflow when holdings lack per-line cost/dates.
            if (assetFlows == 0) {
                Double bookCost = p.getInvestmentAmount();
                if (bookCost == null || bookCost <= 0) {
                    bookCost = sumInvested(p);
                }
                // Last resort: mark-to-market as cost so persona still gets a 0%~ish XIRR
                // instead of a blank dash when broker lines have value but no cost basis.
                if ((bookCost == null || bookCost <= 0) && bookTerminal > 0) {
                    bookCost = bookTerminal;
                }
                if (bookCost != null && bookCost > 0 && bookTerminal > 0) {
                    LocalDate buyDate = resolvePortfolioBuyDate(p, casAsOf, fallbackBuy);
                    if (!buyDate.isBefore(today)) {
                        buyDate = fallbackBuy;
                    }
                    flows.add(new Cashflow(buyDate, -bookCost));
                }
            }
        }
        if (flows.isEmpty() || terminal <= 0) {
            return null;
        }
        flows.add(new Cashflow(today, terminal));
        Double xirr = OverallXirrCalculator.annualizedPct(flows);
        if (xirr == null) {
            // Newton can reject extreme short-window rates; retry with a 1y buy span.
            List<Cashflow> normalized = new ArrayList<>();
            for (Cashflow cf : flows) {
                if (cf.amount() < 0) {
                    normalized.add(new Cashflow(fallbackBuy, cf.amount()));
                } else {
                    normalized.add(new Cashflow(today, cf.amount()));
                }
            }
            xirr = OverallXirrCalculator.annualizedPct(normalized);
        }
        return round2(xirr);
    }

    private static Double resolveAssetCost(AssetModel a) {
        if (a == null) {
            return null;
        }
        if (a.getInvestmentValue() != null && a.getInvestmentValue() > 0) {
            return a.getInvestmentValue();
        }
        if (a.getAvgBuyingPrice() != null && a.getAvgBuyingPrice() > 0
                && a.getQuantity() != null && a.getQuantity() > 0) {
            return a.getAvgBuyingPrice() * a.getQuantity();
        }
        if (a.getImportMarkPrice() != null && a.getImportMarkPrice() > 0
                && a.getQuantity() != null && a.getQuantity() > 0) {
            return a.getImportMarkPrice() * a.getQuantity();
        }
        // Match sumInvested: some CAS lines only store avgBuyingPrice as total cost.
        if (a.getAvgBuyingPrice() != null && a.getAvgBuyingPrice() > 0) {
            return a.getAvgBuyingPrice();
        }
        if (a.getImportMarkPrice() != null && a.getImportMarkPrice() > 0) {
            return a.getImportMarkPrice();
        }
        return null;
    }

    private static LocalDate resolveAssetBuyDate(
            AssetModel a, PortfolioModelV1 p, LocalDate casAsOf, LocalDate fallbackBuy) {
        LocalDate fromAsset = OverallXirrCalculator.toDate(a != null ? a.getCreatedAt() : null);
        if (fromAsset != null) {
            return fromAsset;
        }
        return resolvePortfolioBuyDate(p, casAsOf, fallbackBuy);
    }

    private static LocalDate resolvePortfolioBuyDate(
            PortfolioModelV1 p, LocalDate casAsOf, LocalDate fallbackBuy) {
        LocalDate fromCreated = OverallXirrCalculator.toDate(p != null ? p.getCreatedAt() : null);
        if (fromCreated != null) {
            return fromCreated;
        }
        LocalDate fromUpdated = OverallXirrCalculator.toDate(p != null ? p.getUpdatedAt() : null);
        if (fromUpdated != null) {
            return fromUpdated;
        }
        if (casAsOf != null) {
            return casAsOf;
        }
        return fallbackBuy;
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
        double stored = p.getTotalValue() != null ? p.getTotalValue() : 0.0;
        double lines = sumCurrent(p);
        double current = stored > 0 ? stored : lines;
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
                .dematBoid(p.getDematBoid())
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
