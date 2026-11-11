package com.portfolio.api;

import com.am.common.amcommondata.document.portfolio.PortfolioDocument;
import com.am.common.amcommondata.model.PortfolioModelV1;
import com.am.common.amcommondata.model.PortfolioSnapshotModel;
import com.am.common.amcommondata.model.accounts.AccountsHubModel;
import com.am.common.amcommondata.model.cas.CasDocumentMeta;
import com.am.common.amcommondata.model.cas.CasMfFolioGroup;
import com.am.common.amcommondata.model.demat.DematAccountModel;
import com.am.common.amcommondata.model.enums.PortfolioKind;
import com.am.common.amcommondata.model.family.FamilyMember;
import com.am.common.amcommondata.model.nps.NpsDocumentMeta;
import com.am.common.amcommondata.model.nps.NpsSchemeLine;
import com.am.common.amcommondata.repository.portfolio.PortfolioDocumentRepository;
import com.am.common.amcommondata.service.PortfolioService;
import com.am.common.amcommondata.service.PortfolioSnapshotService;
import com.am.security.context.UserContext;
import com.portfolio.analytics.intelligence.AggregatePortfolioLoader;
import com.portfolio.api.model.AssetClassReplaceRequest;
import com.portfolio.api.model.AssetClassReplaceResponse;
import com.portfolio.api.model.FamilyMemberWriteRequest;
import com.portfolio.api.model.PortfolioBasicInfo;
import com.portfolio.api.security.PortfolioOwnerAssert;
import com.portfolio.model.TimeInterval;
import com.portfolio.model.history.HistoryJobMode;
import com.portfolio.model.history.HistoryJobPhase;
import com.portfolio.model.history.HistoryStatus;
import com.portfolio.model.history.PortfolioHistoryResponse;
import com.portfolio.model.history.PortfolioHistoryStatusResponse;
import com.portfolio.model.portfolio.IntradayDataPoint;
import com.portfolio.model.portfolio.PortfolioAnalysis;
import com.portfolio.model.portfolio.PortfolioHoldings;
import com.portfolio.model.portfolio.v1.PortfolioSummaryV1;
import com.portfolio.redis.service.ActiveMarketSymbolPublisher;
import com.portfolio.redis.service.PortfolioHoldingsRedisService;
import com.portfolio.redis.service.PortfolioIntelligenceRedisService;
import com.portfolio.redis.service.PortfolioSummaryRedisService;
import com.portfolio.service.NewUserPortfolioFallbackService;
import com.portfolio.service.PortfolioDashboardService;
import com.portfolio.service.accounts.AccountsHubService;
import com.portfolio.service.cas.CasImportService;
import com.portfolio.service.family.FamilyMemberDeleteService;
import com.portfolio.service.family.FamilyMemberService;
import com.portfolio.service.nps.NpsImportService;
import com.portfolio.service.portfolio.BrokerPortfolioDeleteService;
import com.portfolio.service.portfolio.PortfolioIntradayService;
import com.portfolio.service.resolver.PortfolioEquitySymbolNormalizer;
import com.portfolio.service.scheduler.PortfolioHistoryJobService;
import com.portfolio.service.scheduler.PortfolioHistoryScheduler;
import com.portfolio.service.scheduler.SnapshotCatchUpService;

import io.swagger.v3.oas.annotations.Hidden;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/v1/portfolios")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Portfolio Management", description = "Endpoints for managing user portfolios")
public class PortfolioController {

    private final PortfolioDashboardService portfolioDashboardService;
    private final PortfolioService portfolioService;
    private final PortfolioHistoryScheduler portfolioHistoryScheduler;
    private final PortfolioSnapshotService portfolioSnapshotService;
    private final SnapshotCatchUpService snapshotCatchUpService;
    private final PortfolioHistoryJobService portfolioHistoryJobService;
    private final PortfolioIntradayService portfolioIntradayService;
    private final ActiveMarketSymbolPublisher activeMarketSymbolPublisher;
    private final PortfolioEquitySymbolNormalizer portfolioEquitySymbolNormalizer;
    private final NewUserPortfolioFallbackService newUserPortfolioFallbackService;
    private final PortfolioOwnerAssert portfolioOwnerAssert;
    private final PortfolioHoldingsRedisService portfolioHoldingsRedisService;
    private final PortfolioSummaryRedisService portfolioSummaryRedisService;
    private final PortfolioIntelligenceRedisService portfolioIntelligenceRedisService;
    private final AggregatePortfolioLoader aggregatePortfolioLoader;
    private final BrokerPortfolioDeleteService brokerPortfolioDeleteService;
    private final CasImportService casImportService;
    private final NpsImportService npsImportService;
    private final AccountsHubService accountsHubService;
    private final FamilyMemberService familyMemberService;
    private final FamilyMemberDeleteService familyMemberDeleteService;

    @Value("${app.jwt.internal-secret}")
    private String internalSecret;

    @Operation(
            summary = "Accounts hub",
            description = "Identity + demat/MF directory + NPS meta + per-broker portfolio amounts for dashboard",
            operationId = "getAccountsHub")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Accounts hub payload"),
            @ApiResponse(responseCode = "401", description = "Unauthenticated")
    })
    @GetMapping("/accounts-hub")
    public ResponseEntity<AccountsHubModel> getAccountsHub() {
        String userId = UserContext.getUserIdOrThrow();
        return ResponseEntity.ok(accountsHubService.hubForUser(userId));
    }

    @Operation(summary = "List soft family members", operationId = "listFamilyMembers")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Family members"),
            @ApiResponse(responseCode = "401", description = "Unauthenticated")
    })
    @GetMapping("/family-members")
    public ResponseEntity<List<FamilyMember>> listFamilyMembers() {
        String userId = UserContext.getUserIdOrThrow();
        return ResponseEntity.ok(familyMemberService.listForOwner(userId));
    }

    @Operation(summary = "Get soft family member", operationId = "getFamilyMember")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Family member"),
            @ApiResponse(responseCode = "404", description = "Not found or not owned")
    })
    @GetMapping("/family-members/{familyMemberId}")
    public ResponseEntity<FamilyMember> getFamilyMember(
            @PathVariable String familyMemberId) {
        String userId = UserContext.getUserIdOrThrow();
        return ResponseEntity.ok(familyMemberService.getOwned(familyMemberId, userId));
    }

    @Operation(summary = "Create soft family member", operationId = "createFamilyMember")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "201", description = "Created"),
            @ApiResponse(responseCode = "400", description = "Invalid body"),
            @ApiResponse(responseCode = "409", description = "Duplicate name or email")
    })
    @PostMapping("/family-members")
    public ResponseEntity<FamilyMember> createFamilyMember(
            @RequestBody FamilyMemberWriteRequest body) {
        String userId = UserContext.getUserIdOrThrow();
        FamilyMember created = familyMemberService.createManual(
                userId,
                body != null ? body.getDisplayName() : null,
                body != null ? body.getEmail() : null);
        return ResponseEntity.status(201).body(created);
    }

    @Operation(summary = "Update soft family member", operationId = "updateFamilyMember")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Updated"),
            @ApiResponse(responseCode = "404", description = "Not found or not owned")
    })
    @PutMapping("/family-members/{familyMemberId}")
    public ResponseEntity<FamilyMember> updateFamilyMember(
            @PathVariable String familyMemberId,
            @RequestBody FamilyMemberWriteRequest body) {
        String userId = UserContext.getUserIdOrThrow();
        return ResponseEntity.ok(familyMemberService.updateOwned(
                familyMemberId,
                userId,
                body != null ? body.getDisplayName() : null,
                body != null ? body.getEmail() : null));
    }

    @Operation(
            summary = "Delete soft family member",
            description = "Cascade-deletes tagged broker portfolios, CAS/NPS imports for that persona, then the member. Primary books untouched.",
            operationId = "deleteFamilyMember")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "204", description = "Deleted"),
            @ApiResponse(responseCode = "404", description = "Not found or not owned")
    })
    @DeleteMapping("/family-members/{familyMemberId}")
    public ResponseEntity<Void> deleteFamilyMember(@PathVariable String familyMemberId) {
        String userId = UserContext.getUserIdOrThrow();
        familyMemberDeleteService.deleteOwnedMember(familyMemberId, userId);
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "CAS account directory", description = "Demat + MF accounts from latest CDSL CAS import", operationId = "listCasAccounts")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "CAS accounts"),
            @ApiResponse(responseCode = "401", description = "Unauthenticated")
    })
    @GetMapping("/accounts")
    public ResponseEntity<List<DematAccountModel>> listCasAccounts() {
        String userId = UserContext.getUserIdOrThrow();
        return ResponseEntity.ok(casImportService.accounts(userId));
    }

    @Operation(summary = "CAS account by number", operationId = "getCasAccount")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "CAS account"),
            @ApiResponse(responseCode = "404", description = "Account not found")
    })
    @GetMapping("/accounts/{accountNo}")
    public ResponseEntity<DematAccountModel> getCasAccount(
            @PathVariable String accountNo) {
        String userId = UserContext.getUserIdOrThrow();
        return casImportService.account(userId, accountNo)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @Operation(summary = "Latest CAS import meta", operationId = "getLatestCas")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Latest CAS meta"),
            @ApiResponse(responseCode = "404", description = "No CAS import yet")
    })
    @GetMapping("/cas/latest")
    public ResponseEntity<CasDocumentMeta> getLatestCas() {
        String userId = UserContext.getUserIdOrThrow();
        return casImportService.latestMeta(userId)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @Operation(summary = "Latest NPS import meta", operationId = "getLatestNps")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Latest NPS meta"),
            @ApiResponse(responseCode = "404", description = "No NPS import yet")
    })
    @GetMapping("/nps/latest")
    public ResponseEntity<NpsDocumentMeta> getLatestNps() {
        String userId = UserContext.getUserIdOrThrow();
        return npsImportService.latestMeta(userId)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @Operation(summary = "NPS scheme lines for portfolio", operationId = "listPortfolioNpsSchemes")
    @GetMapping("/{portfolioId}/nps/schemes")
    public ResponseEntity<List<NpsSchemeLine>> listPortfolioNpsSchemes(
            @PathVariable String portfolioId) {
        PortfolioModelV1 owned = portfolioOwnerAssert.requireOwner(portfolioId);
        return ResponseEntity.ok(npsImportService.schemesForPortfolio(owned.getOwner(), portfolioId));
    }

    @Operation(summary = "MF folio groups for portfolio (CAS)", operationId = "listPortfolioFolios")
    @GetMapping("/{portfolioId}/folios")
    public ResponseEntity<List<CasMfFolioGroup>> listPortfolioFolios(
            @PathVariable String portfolioId) {
        PortfolioModelV1 owned = portfolioOwnerAssert.requireOwner(portfolioId);
        return ResponseEntity.ok(casImportService.foliosForPortfolio(owned.getOwner(), portfolioId));
    }

    @Operation(summary = "MF folio detail (CAS)", operationId = "getPortfolioFolio")
    @GetMapping("/{portfolioId}/folios/{folio}")
    public ResponseEntity<CasMfFolioGroup> getPortfolioFolio(
            @PathVariable String portfolioId,
            @PathVariable String folio) {
        PortfolioModelV1 owned = portfolioOwnerAssert.requireOwner(portfolioId);
        return casImportService.folio(owned.getOwner(), folio)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @Operation(summary = "Get intraday data for all portfolios", operationId = "getAllPortfoliosIntraday")
    @GetMapping("/intraday")
    public ResponseEntity<List<IntradayDataPoint>> getAllPortfoliosIntraday(
            @RequestParam(required = false) String persona) {
        String userId = UserContext.getUserIdOrThrow();
        NewUserPortfolioFallbackService.DemoResolution res = newUserPortfolioFallbackService.resolveRequest(userId, null);
        String traceId = MDC.get("traceId");
        log.info("[Intraday] Request for all portfolios, user={}, persona={}, traceId={}",
                res.userId(), persona, traceId);
        return ResponseEntity.ok(portfolioIntradayService.getIntraday(res.userId(), res.portfolioId(), persona));
    }

    @Operation(summary = "Get intraday data for specific portfolio", operationId = "getPortfolioIntraday")
    @GetMapping("/{portfolioId}/intraday")
    public ResponseEntity<List<IntradayDataPoint>> getPortfolioIntraday(
            @PathVariable String portfolioId) {
        String userId = UserContext.getUserIdOrThrow();
        NewUserPortfolioFallbackService.DemoResolution res = newUserPortfolioFallbackService.resolveRequest(userId, portfolioId);
        String traceId = MDC.get("traceId");
        log.info("[Intraday] Request for portfolio={}, user={}, traceId={}", res.portfolioId(), res.userId(), traceId);
        return ResponseEntity.ok(portfolioIntradayService.getIntraday(res.userId(), res.portfolioId()));
    }

    @Operation(summary = "Get portfolio by ID", description = "Retrieves detailed portfolio information for a specific portfolio ID", operationId = "getPortfolioById")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Portfolio found", content = @Content(mediaType = "application/json", schema = @Schema(implementation = PortfolioModelV1.class))),
            @ApiResponse(responseCode = "400", description = "Invalid portfolio ID format"),
            @ApiResponse(responseCode = "404", description = "Portfolio not found")
    })
    @GetMapping("/{portfolioId}")
    public ResponseEntity<PortfolioModelV1> getPortfolioById(
            @Parameter(description = "Portfolio ID (UUID format)") @PathVariable String portfolioId) {
        log.info("PortfolioController - getPortfolioById called with portfolioId: {}", portfolioId);

        try {
            PortfolioModelV1 portfolio = portfolioService.getPortfolioById(UUID.fromString(portfolioId));
            log.info("PortfolioController - getPortfolioById - Portfolio found: {}", portfolio != null ? "yes" : "no");
            if (portfolio == null) {
                return ResponseEntity.notFound().build();
            }
            return ResponseEntity.ok(portfolio);
        } catch (IllegalArgumentException e) {
            log.error("PortfolioController - getPortfolioById - Invalid portfolio ID: {}", portfolioId, e);
            return ResponseEntity.badRequest().build();
        }
    }

    @Operation(
            summary = "Replace one asset-class list",
            description = "Replaces bonds, commodities, or cash holdings on a BROKER portfolio. Equities and other classes are unchanged.",
            operationId = "replaceAssetClassList")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Class list updated"),
            @ApiResponse(responseCode = "400", description = "Invalid request"),
            @ApiResponse(responseCode = "403", description = "Not portfolio owner"),
            @ApiResponse(responseCode = "404", description = "Portfolio not found")
    })
    @PutMapping("/{portfolioId}/asset-classes/{assetClass}")
    public ResponseEntity<AssetClassReplaceResponse> replaceAssetClassList(
            @PathVariable String portfolioId,
            @PathVariable String assetClass,
            @RequestBody(required = false) AssetClassReplaceRequest request) {
        PortfolioModelV1 owned = portfolioOwnerAssert.requireOwner(portfolioId);
        String userId = owned.getOwner();
        try {
            PortfolioModelV1 saved = portfolioService.replaceAssetClassList(
                    owned.getId(),
                    userId,
                    assetClass,
                    request != null ? request.getItems() : List.of());
            String pid = saved.getId() != null ? saved.getId().toString() : portfolioId;
            portfolioHoldingsRedisService.evictPortfolioHoldings(userId, pid);
            portfolioSummaryRedisService.evictPortfolioSummary(userId, pid);
            portfolioIntelligenceRedisService.evict(pid);
            portfolioIntelligenceRedisService.evictAggregateForUser(userId);
            aggregatePortfolioLoader.evict(userId);
            activeMarketSymbolPublisher.publishFromPortfolio(saved);
            int count = switch (assetClass == null ? "" : assetClass.trim().toLowerCase()) {
                case "bonds" -> saved.getBonds() != null ? saved.getBonds().size() : 0;
                case "commodities" -> saved.getCommodities() != null ? saved.getCommodities().size() : 0;
                case "cash" -> saved.getCash() != null ? saved.getCash().size() : 0;
                default -> 0;
            };
            return ResponseEntity.ok(AssetClassReplaceResponse.builder()
                    .portfolioId(saved.getId())
                    .assetClass(assetClass)
                    .itemCount(count)
                    .totalValue(saved.getTotalValue())
                    .build());
        } catch (SecurityException e) {
            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN, e.getMessage());
        } catch (IllegalArgumentException e) {
            if (e.getMessage() != null && e.getMessage().contains("not found")) {
                throw new ResponseStatusException(
                        HttpStatus.NOT_FOUND, e.getMessage());
            }
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST, e.getMessage());
        }
    }

    @Operation(summary = "Get all portfolios for user", description = "Retrieves all portfolios associated with a specific user ID", operationId = "getPortfolios")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "List of portfolios retrieved successfully"),
            @ApiResponse(responseCode = "404", description = "No portfolios found for user")
    })
    @GetMapping
    public ResponseEntity<List<PortfolioModelV1>> getPortfolios() {
        String userId = UserContext.getUserIdOrThrow();
        log.info("PortfolioController - getPortfolios called with userId: {}", userId);

        List<PortfolioModelV1> portfolios = portfolioService.getPortfoliosByUserId(userId);
        log.info("PortfolioController - getPortfolios - Found {} portfolios for user: {}",
                portfolios != null ? portfolios.size() : 0, userId);

        return ResponseEntity.ok(portfolios);
    }

    @Operation(summary = "Get portfolio IDs and names", description = "Retrieves a lightweight list of portfolio IDs and names for all user portfolios. Empty list when the user has no portfolios.", operationId = "getPortfolioBasicDetails")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Portfolio list retrieved successfully (may be empty)")
    })
    @GetMapping("/list")
    public ResponseEntity<List<PortfolioBasicInfo>> getPortfolioBasicDetails() {
        String userId = UserContext.getUserIdOrThrow();
        log.info("PortfolioController - getPortfolioBasicDetails called with userId: {}", userId);

        List<PortfolioBasicInfo> basicInfoList = newUserPortfolioFallbackService.listBasicPortfolios(userId).stream()
                .map(row -> {
                    PortfolioBasicInfo info = new PortfolioBasicInfo();
                    info.setPortfolioId(row.portfolioId());
                    info.setPortfolioName(row.portfolioName());
                    info.setKind(row.kind());
                    info.setGapMissingCount(row.gapMissingCount());
                    info.setDummy(row.dummy());
                    info.setBrokerType(row.brokerType());
                    info.setAssetCount(row.assetCount());
                    info.setTotalValue(row.totalValue());
                    info.setHasHoldings(row.hasHoldings());
                    info.setFamilyMemberId(row.familyMemberId());
                    info.setFamilyMemberName(row.familyMemberName());
                    return info;
                })
                .collect(Collectors.toList());

        log.info("PortfolioController - getPortfolioBasicDetails - Found {} portfolio basic details for user: {}",
                basicInfoList.size(), userId);

        return ResponseEntity.ok(basicInfoList);
    }

    @Operation(summary = "Restore demo portfolio eligibility", description = "Clears any legacy per-user demo dismiss flag. The demo is shown whenever the user has zero real portfolios; uploading a real portfolio hides it automatically.", operationId = "dismissDemoPortfolio")
    @DeleteMapping("/demo")
    public ResponseEntity<Void> dismissDemoPortfolio() {
        String userId = UserContext.getUserIdOrThrow();
        log.info("PortfolioController - dismissDemoPortfolio (legacy clear) called for userId: {}", userId);
        newUserPortfolioFallbackService.dismissForUser(userId);
        return ResponseEntity.noContent().build();
    }

    @Operation(
            summary = "Delete a broker portfolio",
            description = "Hard-deletes the caller's portfolio from Mongo, evicts caches, and publishes action=DELETE on am-portfolio-update so analysis and trade clear. Path portfolioId + JWT owner only.",
            operationId = "deletePortfolio")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "204", description = "Portfolio deleted"),
            @ApiResponse(responseCode = "400", description = "Invalid portfolio ID"),
            @ApiResponse(responseCode = "403", description = "Not portfolio owner"),
            @ApiResponse(responseCode = "404", description = "Portfolio not found")
    })
    @DeleteMapping("/{portfolioId}")
    public ResponseEntity<Void> deletePortfolio(
            @Parameter(description = "Portfolio ID (UUID)") @PathVariable String portfolioId) {
        String userId = UserContext.getUserIdOrThrow();
        log.info("PortfolioController - deletePortfolio portfolioId={} userId={}", portfolioId, userId);
        brokerPortfolioDeleteService.deleteOwnedPortfolio(portfolioId, userId);
        return ResponseEntity.noContent().build();
    }

    /**
     * Internal live portfolio IDs — called by am-analysis orphan guard / reconcile.
     * Declared before {@code /internal/{portfolioId}} so {@code ids} is never captured as a path var.
     */
    @Hidden
    @Operation(summary = "List portfolio IDs for owner (internal)", operationId = "listPortfolioIdsInternal")
    @GetMapping("/internal/ids")
    public ResponseEntity<List<String>> listPortfolioIdsInternal(
            @RequestParam String ownerId,
            @RequestHeader(value = "X-Internal-Secret", required = false) String secret) {
        if (secret == null || !internalSecret.equals(secret)) {
            log.warn("Unauthorized internal ids list attempt rejected ownerId={}", ownerId);
            return ResponseEntity.status(403).build();
        }
        try {
            return ResponseEntity.ok(brokerPortfolioDeleteService.listPortfolioIdsForOwner(ownerId));
        } catch (ResponseStatusException e) {
            return ResponseEntity.status(e.getStatusCode()).build();
        }
    }

    /**
     * Internal ownership check — called by am-trade-management before lazy local stub create.
     */
    @Hidden
    @Operation(summary = "Verify broker portfolio ownership (internal)", operationId = "verifyPortfolioInternal")
    @GetMapping("/internal/{portfolioId}")
    public ResponseEntity<Void> verifyPortfolioInternal(
            @PathVariable String portfolioId,
            @RequestParam String ownerId,
            @RequestHeader(value = "X-Internal-Secret", required = false) String secret) {
        if (secret == null || !internalSecret.equals(secret)) {
            log.warn("Unauthorized internal verify attempt rejected portfolioId={}", portfolioId);
            return ResponseEntity.status(403).build();
        }
        if (ownerId == null || ownerId.isBlank()) {
            return ResponseEntity.badRequest().build();
        }
        try {
            brokerPortfolioDeleteService.assertOwnedBrokerPortfolio(portfolioId, ownerId);
            return ResponseEntity.noContent().build();
        } catch (ResponseStatusException e) {
            return ResponseEntity.status(e.getStatusCode()).build();
        }
    }

    /**
     * Internal broker delete — called by am-trade-management so Trade→Portfolio
     * delete is synchronous Mongo+cache+Kafka instead of fire-and-forget Kafka alone.
     */
    @Hidden
    @Operation(summary = "Delete broker portfolio (internal)", operationId = "deletePortfolioInternal")
    @DeleteMapping("/internal/{portfolioId}")
    public ResponseEntity<Void> deletePortfolioInternal(
            @PathVariable String portfolioId,
            @RequestParam String ownerId,
            @RequestHeader(value = "X-Internal-Secret", required = false) String secret) {
        if (secret == null || !internalSecret.equals(secret)) {
            log.warn("Unauthorized internal delete attempt rejected portfolioId={}", portfolioId);
            return ResponseEntity.status(403).build();
        }
        if (ownerId == null || ownerId.isBlank()) {
            return ResponseEntity.badRequest().build();
        }
        log.info("PortfolioController - deletePortfolioInternal portfolioId={} ownerId={}", portfolioId, ownerId);
        brokerPortfolioDeleteService.deleteOwnedPortfolio(portfolioId, ownerId);
        return ResponseEntity.noContent().build();
    }

    /**
     * Internal sync endpoint — called by am-trade-management when a portfolio is created or updated in trade.
     * This upserts the portfolio into am-portfolio's database so the user can see it in the Portfolio portal.
     * Uses the internal JWT secret to authenticate (service-to-service call).
     *
     * <p>This provides a reliable, synchronous alternative to Kafka for the trade → portfolio direction.
     */
    @Hidden
    @Operation(summary = "Sync portfolio from trade management (internal)", description = "Upserts a portfolio received from am-trade-management into the portfolio database. Service-to-service call.", operationId = "syncPortfolioFromTrade")
    @PostMapping("/sync")
    public ResponseEntity<PortfolioModelV1> syncPortfolioFromTrade(
            @RequestBody PortfolioModelV1 portfolioModel,
            @RequestHeader(value = "X-Internal-Secret", required = false) String secret) {
        if (secret == null || !internalSecret.equals(secret)) {
            log.warn("Unauthorized sync attempt rejected");
            return ResponseEntity.status(403).build();
        }
        if (portfolioModel == null || portfolioModel.getOwner() == null) {
            return ResponseEntity.badRequest().build();
        }
        log.info("PortfolioController - syncPortfolioFromTrade: portfolioId={} name={} owner={}",
                portfolioModel.getId(), portfolioModel.getName(), portfolioModel.getOwner());

        try {
            // HTTP sync bypasses Kafka mapper — normalize symbols here before Mongo write.
            portfolioEquitySymbolNormalizer.normalizePortfolio(portfolioModel);
            List<PortfolioModelV1> existing = portfolioService.getPortfoliosByUserId(portfolioModel.getOwner());
            boolean brokerExisted = existing != null && existing.stream()
                    .filter(p -> p.getPortfolioKind() == null
                            || PortfolioKind.isBroker(p.getPortfolioKind()))
                    .anyMatch(p -> Objects.equals(p.getBrokerType(), portfolioModel.getBrokerType()));
            boolean otherBrokers = existing != null && existing.stream()
                    .filter(p -> p.getPortfolioKind() == null
                            || PortfolioKind.isBroker(p.getPortfolioKind()))
                    .anyMatch(p -> portfolioModel.getBrokerType() == null
                            || !Objects.equals(p.getBrokerType(), portfolioModel.getBrokerType()));

            PortfolioModelV1 saved = portfolioService.updateTradePortfolio(portfolioModel);
            if (saved != null) {
                activeMarketSymbolPublisher.publishFromPortfolio(saved);
                String portfolioId = saved.getId() != null ? saved.getId().toString() : null;
                if (portfolioId != null && saved.getOwner() != null) {
                    portfolioHoldingsRedisService.evictPortfolioHoldings(saved.getOwner(), portfolioId);
                    portfolioSummaryRedisService.evictPortfolioSummary(saved.getOwner(), portfolioId);
                    portfolioIntelligenceRedisService.evict(portfolioId);
                    portfolioIntelligenceRedisService.evictAggregateForUser(saved.getOwner());
                    aggregatePortfolioLoader.evict(saved.getOwner());
                }
                HistoryJobMode mode = (!brokerExisted && otherBrokers)
                        ? HistoryJobMode.MERGE_BROKER
                        : HistoryJobMode.FULL;
                if (!brokerExisted || "REPLACE_ALL".equals(portfolioModel.getLastTradeAction())) {
                    try {
                        portfolioHistoryJobService.enqueue(saved.getOwner(), mode, portfolioId);
                    } catch (Exception enqueueEx) {
                        log.warn("PortfolioController - history enqueue failed after sync portfolioId={}: {}",
                                portfolioId, enqueueEx.getMessage());
                    }
                }
            }
            log.info("PortfolioController - syncPortfolioFromTrade: saved portfolioId={}", saved != null ? saved.getId() : "null");
            return ResponseEntity.ok(saved);
        } catch (Exception e) {
            log.error("PortfolioController - syncPortfolioFromTrade failed: {}", e.getMessage(), e);
            return ResponseEntity.internalServerError().build();
        }
    }




    @Operation(summary = "Trigger snapshot", description = "Manually triggers the end of day portfolio snapshot generation. Returns immediately — job runs in the background.", operationId = "triggerSnapshot")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "202", description = "Snapshot job accepted and running in background")
    })
    @PostMapping("/trigger-snapshot")
    public ResponseEntity<String> triggerSnapshot() {
        log.info("PortfolioController - triggerSnapshot called manually via API");
        portfolioHistoryScheduler.runEndOfDayJobAsync();
        return ResponseEntity.accepted().body("Snapshot job accepted. Running in background — check server logs for progress.");
    }

    @Hidden
    @Operation(summary = "Get portfolio analysis", description = "Retrieves detailed analysis for a specific portfolio (hidden from API docs)", operationId = "getPortfolioAnalysis")
    @GetMapping("/{portfolioId}/analysis")
    public ResponseEntity<PortfolioAnalysis> getPortfolioAnalysis(
            @PathVariable String portfolioId,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size,
            @RequestParam(required = false) String interval) {
        String userId = UserContext.getUserIdOrThrow();

        NewUserPortfolioFallbackService.DemoResolution res = newUserPortfolioFallbackService.resolveRequest(userId, portfolioId);


        log.info(
                "PortfolioController - getPortfolioAnalysis called - Portfolio: {}, User: {}, Page: {}, Size: {}, Interval: {}",
                res.portfolioId(), res.userId(), page, size, interval != null ? interval : "null");

        try {
            TimeInterval timeInterval = TimeInterval.fromCode(interval);
            PortfolioAnalysis analysis = portfolioDashboardService.analyzePortfolio(
                    res.portfolioId(), res.userId(), page, size, timeInterval);

            if (analysis == null) {
                log.warn("PortfolioController - getPortfolioAnalysis - No analysis found for portfolio: {}",
                        res.portfolioId());
                return ResponseEntity.notFound().build();
            }

            log.info("PortfolioController - getPortfolioAnalysis - Successfully retrieved analysis for portfolio: {}",
                    res.portfolioId());
            return ResponseEntity.ok(analysis);
        } catch (IllegalArgumentException e) {
            log.error("PortfolioController - getPortfolioAnalysis - Invalid interval: {}", interval, e);
            return ResponseEntity.badRequest().build();
        }
    }

    @Operation(summary = "Get portfolio summary", description = "Retrieves a summary of all portfolios for a user with performance metrics. Optionally filter by specific portfolio ID.", operationId = "getPortfolioSummary")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Portfolio summary retrieved successfully"),
            @ApiResponse(responseCode = "404", description = "No portfolio summary found for user")
    })
    @GetMapping("/summary")
    public ResponseEntity<PortfolioSummaryV1> getPortfolioSummary(
            @Parameter(description = "Optional portfolio ID to filter results for specific portfolio") @RequestParam(required = false) String portfolioId,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size,
            @RequestParam(required = false) String interval,
            @Parameter(description = "All-books scope: you, household, or a family member id")
            @RequestParam(required = false) String persona) {
        String userId = UserContext.getUserIdOrThrow();

        NewUserPortfolioFallbackService.DemoResolution res = newUserPortfolioFallbackService.resolveRequest(userId, portfolioId);


        log.info(
                "PortfolioController - getPortfolioSummary called - User: {}, Portfolio: {}, Page: {}, Size: {}, Interval: {}, Persona: {}",
                res.userId(), res.portfolioId() != null ? res.portfolioId() : "all", page, size, interval != null ? interval : "null", persona);

        try {
            TimeInterval timeInterval = TimeInterval.fromCode(interval);
            PortfolioSummaryV1 portfolioSummary;

            if (res.portfolioId() != null && !res.portfolioId().trim().isEmpty() && !res.portfolioId().equals(res.userId())) {
                // Filter by specific portfolio
                log.info("PortfolioController - getPortfolioSummary - Filtering by portfolio: {}", res.portfolioId());
                portfolioSummary = portfolioDashboardService.overviewPortfolio(res.userId(), res.portfolioId(), timeInterval);
            } else {
                // Get summary for all portfolios
                portfolioSummary = portfolioDashboardService.overviewPortfolio(res.userId(), timeInterval, persona);
            }

            if (portfolioSummary == null) {
                log.info("PortfolioController - getPortfolioSummary - No summary found for user: {} and portfolio: {}. Returning empty state.",
                        res.userId(), res.portfolioId() != null ? res.portfolioId() : "all");
                return ResponseEntity.ok(PortfolioSummaryV1.empty());
            }

            log.info(
                    "PortfolioController - getPortfolioSummary - Successfully retrieved summary for user: {} and portfolio: {}",
                    res.userId(), res.portfolioId() != null ? res.portfolioId() : "all");
            return ResponseEntity.ok(portfolioSummary);
        } catch (IllegalArgumentException e) {
            log.error("PortfolioController - getPortfolioSummary - Invalid interval: {}", interval, e);
            return ResponseEntity.badRequest().build();
        }
    }

    @Operation(summary = "Get portfolio holdings", description = "Retrieves all holdings across portfolios for a user with current values. Optionally filter by specific portfolio ID.", operationId = "getPortfolioHoldings")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Portfolio holdings retrieved successfully"),
            @ApiResponse(responseCode = "404", description = "No holdings found for user")
    })
    @GetMapping("/holdings")
    public ResponseEntity<PortfolioHoldings> getPortfolioHoldings(
            @Parameter(description = "Optional portfolio ID to filter results for specific portfolio") @RequestParam(required = false) String portfolioId,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size,
            @RequestParam(required = false) String interval,
            @Parameter(description = "Optional CAS asset class filter: equity|mutual_fund_demat|debt|mutual_fund_folio")
            @RequestParam(required = false) String assetClass,
            @Parameter(description = "All-books scope: you, household, or a family member id")
            @RequestParam(required = false) String persona) {
        String userId = UserContext.getUserIdOrThrow();

        NewUserPortfolioFallbackService.DemoResolution res = newUserPortfolioFallbackService.resolveRequest(userId, portfolioId);


        log.info(
                "PortfolioController - getPortfolioHoldings called - User: {}, Portfolio: {}, Page: {}, Size: {}, Interval: {}, assetClass: {}",
                res.userId(), res.portfolioId() != null ? res.portfolioId() : "all", page, size, interval != null ? interval : "null",
                assetClass);

        try {
            TimeInterval timeInterval = TimeInterval.fromCode(interval);
            PortfolioHoldings portfolioHoldings;

            if (res.portfolioId() != null && !res.portfolioId().trim().isEmpty() && !res.portfolioId().equals(res.userId())) {
                // Filter by specific portfolio
                log.info("PortfolioController - getPortfolioHoldings - Filtering by portfolio: {}", res.portfolioId());
                portfolioHoldings = portfolioDashboardService.getPortfolioHoldings(
                        res.userId(), res.portfolioId(), timeInterval, assetClass);
            } else {
                // Get holdings for all portfolios
                portfolioHoldings = portfolioDashboardService.getPortfolioHoldings(
                        res.userId(), timeInterval, persona, assetClass);
            }

            if (portfolioHoldings == null) {
                log.info(
                        "PortfolioController - getPortfolioHoldings - No holdings found for user: {} and portfolio: {}. Returning empty state.",
                        res.userId(), res.portfolioId() != null ? res.portfolioId() : "all");
                return ResponseEntity.ok(PortfolioHoldings.empty());
            }

            log.info(
                    "PortfolioController - getPortfolioHoldings - Successfully retrieved holdings for user: {} and portfolio: {}",
                    res.userId(), res.portfolioId() != null ? res.portfolioId() : "all");
            return ResponseEntity.ok(portfolioHoldings);
        } catch (IllegalArgumentException e) {
            log.error("PortfolioController - getPortfolioHoldings - Invalid interval: {}", interval, e);
            return ResponseEntity.badRequest().build();
        }
    }

    @Operation(summary = "Get portfolio history", description = "Retrieves the snapshot history of all portfolios for a user with the specified timeframe.", operationId = "getPortfolioHistory")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Portfolio history retrieved successfully"),
            @ApiResponse(responseCode = "404", description = "No history found for user")
    })
    @GetMapping("/history")
    public ResponseEntity<PortfolioHistoryResponse> getPortfolioHistory(
            @RequestParam(required = false, defaultValue = "1M") String timeFrame,
            @RequestParam(required = false) String persona) {
        String userId = UserContext.getUserIdOrThrow();
        NewUserPortfolioFallbackService.DemoResolution res = newUserPortfolioFallbackService.resolveRequest(userId, null);
        log.info("PortfolioController - getPortfolioHistory called - User: {}, TimeFrame: {}, persona: {}",
                res.userId(), timeFrame, persona);

        List<PortfolioSnapshotModel> history = portfolioSnapshotService.getHistory(res.userId(), res.portfolioId(), timeFrame);
        if (history == null) {
            history = Collections.emptyList();
        }
        // persona is accepted for API parity with summary/intraday; aggregate snapshots are household-level today.
        return ResponseEntity.ok(toHistoryResponse(res.userId(), history));
    }

    @Operation(summary = "Get specific portfolio history", description = "Retrieves the snapshot history of a specific portfolio for a user with the specified timeframe.", operationId = "getSpecificPortfolioHistory")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Portfolio history retrieved successfully"),
            @ApiResponse(responseCode = "404", description = "No history found for portfolio")
    })
    @GetMapping("/{portfolioId}/history")
    public ResponseEntity<PortfolioHistoryResponse> getSpecificPortfolioHistory(
            @PathVariable String portfolioId,
            @RequestParam(required = false, defaultValue = "1M") String timeFrame) {
        String userId = UserContext.getUserIdOrThrow();
        NewUserPortfolioFallbackService.DemoResolution res = newUserPortfolioFallbackService.resolveRequest(userId, portfolioId);
        log.info("PortfolioController - getSpecificPortfolioHistory called - User: {}, Portfolio: {}, TimeFrame: {}", res.userId(), res.portfolioId(), timeFrame);

        List<PortfolioSnapshotModel> history = portfolioSnapshotService.getHistory(res.userId(), res.portfolioId(), timeFrame);
        if (history == null) {
            history = Collections.emptyList();
        }
        return ResponseEntity.ok(toHistoryResponse(res.userId(), history));
    }

    @Operation(summary = "Get portfolio history build status", description = "Lightweight poll for chart sync UI while history is building.", operationId = "getPortfolioHistoryStatus")
    @GetMapping("/history/status")
    public ResponseEntity<PortfolioHistoryStatusResponse> getPortfolioHistoryStatus() {
        String userId = UserContext.getUserIdOrThrow();
        NewUserPortfolioFallbackService.DemoResolution res = newUserPortfolioFallbackService.resolveRequest(userId, null);
        return ResponseEntity.ok(portfolioHistoryJobService.getStatus(res.userId()));
    }

    private PortfolioHistoryResponse toHistoryResponse(String userId, List<PortfolioSnapshotModel> points) {
        PortfolioHistoryStatusResponse status;
        try {
            status = portfolioHistoryJobService.getStatus(userId);
        } catch (Exception e) {
            log.warn("PortfolioController - history status lookup failed userId={}: {}", userId, e.getMessage());
            status = PortfolioHistoryStatusResponse.builder()
                    .historyStatus(points.isEmpty() ? HistoryStatus.EMPTY : HistoryStatus.READY)
                    .phase(HistoryJobPhase.READY)
                    .coverageDays(points.size())
                    .build();
        }
        HistoryStatus hs = status.getHistoryStatus();
        if (hs == null || hs == HistoryStatus.EMPTY) {
            hs = points.isEmpty() ? HistoryStatus.EMPTY : HistoryStatus.READY;
        }
        if (status.getPhase() != null
                && status.getPhase() != HistoryJobPhase.READY
                && status.getPhase() != HistoryJobPhase.FAILED) {
            hs = HistoryStatus.BUILDING;
        }
        return PortfolioHistoryResponse.builder()
                .points(points)
                .historyStatus(hs)
                .phase(status.getPhase())
                .startedAt(status.getStartedAt())
                .historyFrom(status.getHistoryFrom())
                .historyTo(status.getHistoryTo())
                .coverageDays(status.getCoverageDays())
                .build();
    }

    /**
     * DEV/ADMIN ONLY — Directly triggers historical snapshot catch-up for any given userId.
     * Usage: POST /v1/portfolios/dev/trigger-catchup?userId=…&mode=FULL&targetPortfolioId=…
     * mode defaults to FULL (rebuild); GAP only fills missing calendar days.
     */
    @Hidden
    @PostMapping("/dev/trigger-catchup")
    public ResponseEntity<String> triggerCatchUpForUser(
            @RequestParam String userId,
            @RequestParam(required = false, defaultValue = "FULL") String mode,
            @RequestParam(required = false) String targetPortfolioId,
            @RequestHeader(value = "X-Internal-Secret", required = false) String secret) {
        if (secret == null || !internalSecret.equals(secret)) {
            return ResponseEntity.status(403).body("Forbidden");
        }
        HistoryJobMode jobMode;
        try {
            jobMode = HistoryJobMode.valueOf(mode.trim().toUpperCase());
        } catch (Exception e) {
            return ResponseEntity.badRequest().body("Invalid mode; use FULL, GAP, or MERGE_BROKER");
        }
        log.info("[DEV] Manual catch-up trigger for userId={} mode={} target={}", userId, jobMode, targetPortfolioId);
        portfolioHistoryJobService.enqueue(userId, jobMode, targetPortfolioId);
        return ResponseEntity.ok("HistoryJob enqueued userId=" + userId + " mode=" + jobMode
                + (targetPortfolioId != null ? " target=" + targetPortfolioId : ""));
    }

    /**
     * DEV/ADMIN ONLY — Hidden from Swagger.
     * Triggers manual EOD snapshot for today or a specific date.
     * Usage: POST /v1/portfolios/dev/trigger-snapshot?userId=sahim99&date=2026-07-28
     */
    @Hidden
    @PostMapping("/dev/trigger-snapshot")
    public ResponseEntity<String> triggerSnapshotForUser(
            @RequestParam String userId,
            @RequestParam(required = false) String date,
            @RequestHeader(value = "X-Internal-Secret", required = false) String secret) {
        if (secret == null || !internalSecret.equals(secret)) {
            return ResponseEntity.status(403).body("Forbidden");
        }
        LocalDate targetDate = date != null ? LocalDate.parse(date) : LocalDate.now();
        log.info("[DEV] Manual snapshot trigger for userId={} date={}", userId, targetDate);
        portfolioHistoryScheduler.runEndOfDayJobForUserAndDateAsync(userId, targetDate);
        return ResponseEntity.ok("Snapshot generation triggered for userId=" + userId + " date=" + targetDate);
    }

    /**
     * DEV/ADMIN ONLY — Hidden from Swagger.
     * Triggers snapshot backfilling for a range of past dates.
     * Usage: POST /v1/portfolios/dev/backfill-snapshots?userId=sahim99&days=30
     */
    @Hidden
    @PostMapping("/dev/backfill-snapshots")
    public ResponseEntity<String> backfillSnapshots(
            @RequestParam String userId,
            @RequestParam(defaultValue = "30") int days,
            @RequestHeader(value = "X-Internal-Secret", required = false) String secret) {
        if (secret == null || !internalSecret.equals(secret)) {
            return ResponseEntity.status(403).body("Forbidden");
        }
        log.info("[DEV] Backfill trigger for userId={} days={}", userId, days);
        portfolioHistoryScheduler.backfillSnapshotsAsync(userId, days);
        return ResponseEntity.ok("Backfill triggered for userId=" + userId + " for last " + days + " days.");
    }

    /**
     * DEV/ADMIN ONLY — Hidden from Swagger.
     * Safely deletes a snapshot for a specific user and date.
     * Usage: DELETE /v1/portfolios/dev/snapshot?userId=sahim99&date=2026-07-30
     */
    @Hidden
    @DeleteMapping("/dev/snapshot")
    public ResponseEntity<String> deleteSnapshot(
            @RequestParam String userId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            @RequestHeader(value = "X-Internal-Secret", required = false) String secret) {
        if (secret == null || !internalSecret.equals(secret)) {
            return ResponseEntity.status(403).body("Forbidden");
        }
        log.info("[DEV] Delete snapshot trigger for userId={} date={}", userId, date);
        portfolioSnapshotService.deleteSnapshot(userId, date);
        return ResponseEntity.ok("Snapshot for userId=" + userId + " on date=" + date + " has been successfully deleted. You can now use the trigger-catchup endpoint to rebuild it.");
    }


    /**
     * DEV/ADMIN ONLY — Hidden from Swagger.
     * Fixes broken "Grow" or "Auto-created GROW" portfolio names in MongoDB.
     */
    @Hidden
    @PostMapping("/dev/migrate-groww")
    public ResponseEntity<String> migrateGrowwNames(
            @Autowired PortfolioDocumentRepository repo,
            @RequestHeader(value = "X-Internal-Secret", required = false) String secret) {
        if (secret == null || !internalSecret.equals(secret)) {
            return ResponseEntity.status(403).body("Forbidden");
        }
        log.info("[DEV] Running Groww name migration...");
        List<PortfolioDocument> allDocs = repo.findAll();
        int updated = 0;
        for (PortfolioDocument doc : allDocs) {
            if ("Grow".equals(doc.getName()) || "Auto-created GROW".equals(doc.getName())) {
                doc.setName("Groww");
                repo.save(doc);
                updated++;
                log.info("Migrated portfolio ID {} to name 'Groww'", doc.getId());
            }
        }
        return ResponseEntity.ok("Migration complete. Updated " + updated + " portfolios.");
    }

    /**
     * DEV/ADMIN ONLY — Re-normalize equity symbols (ISIN → ticker) for stored portfolios.
     * Usage: POST /v1/portfolios/dev/normalize-symbols?userId=optional-user-id
     */
    @Hidden
    @PostMapping("/dev/normalize-symbols")
    public ResponseEntity<Map<String, Object>> normalizeStoredSymbols(
            @RequestParam(required = false) String userId,
            @RequestHeader(value = "X-Internal-Secret", required = false) String secret) {
        if (secret == null || !internalSecret.equals(secret)) {
            return ResponseEntity.status(403).body(Map.of("error", "Forbidden"));
        }

        List<PortfolioModelV1> portfolios;
        if (userId != null && !userId.isBlank()) {
            portfolios = portfolioService.getPortfoliosByUserId(userId);
        } else {
            portfolios = new ArrayList<>();
            for (String uid : portfolioService.getAllUserIds()) {
                portfolios.addAll(portfolioService.getPortfoliosByUserId(uid));
            }
        }

        int updatedPortfolios = 0;
        int normalizedEquities = 0;
        int cachesEvicted = 0;
        Set<String> ownersToRebuild = new LinkedHashSet<>();
        Map<String, String> ownerTargetPortfolio = new HashMap<>();
        for (PortfolioModelV1 portfolio : portfolios) {
            if (portfolio == null || portfolio.getEquityModels() == null) {
                continue;
            }
            // Detect any ticker change (IDEA→VODAFONEIDEA), not only ISIN-as-symbol rows.
            boolean changed = portfolioEquitySymbolNormalizer.normalizePortfolioAndDetectChange(portfolio);
            if (changed) {
                portfolioService.upsertDocumentPortfolio(portfolio);
                updatedPortfolios++;
                normalizedEquities += portfolio.getEquityModels().size();
                if (portfolio.getOwner() != null && !portfolio.getOwner().isBlank()) {
                    ownersToRebuild.add(portfolio.getOwner());
                    if (portfolio.getId() != null) {
                        ownerTargetPortfolio.putIfAbsent(portfolio.getOwner(), portfolio.getId().toString());
                    }
                }
            }
            // Always evict caches so UI cannot keep serving ISIN-as-symbol holdings
            // even when Mongo had no delta (e.g. prior normalize without eviction).
            String owner = portfolio.getOwner();
            if (owner != null && !owner.isBlank()) {
                String portfolioId = portfolio.getId() != null ? portfolio.getId().toString() : null;
                String portfolioName = portfolio.getName();
                portfolioHoldingsRedisService.evictPortfolioHoldings(owner, portfolioId);
                portfolioSummaryRedisService.evictPortfolioSummary(owner, portfolioId);
                if (portfolioName != null && !portfolioName.isBlank()
                        && (portfolioId == null || !portfolioName.equals(portfolioId))) {
                    portfolioHoldingsRedisService.evictPortfolioHoldings(owner, portfolioName);
                    portfolioSummaryRedisService.evictPortfolioSummary(owner, portfolioName);
                }
                if (portfolioId != null) {
                    portfolioIntelligenceRedisService.evict(portfolioId);
                }
                portfolioIntelligenceRedisService.evictAggregateForUser(owner);
                cachesEvicted++;
            }
        }

        for (String owner : ownersToRebuild) {
            String target = ownerTargetPortfolio.get(owner);
            portfolioHistoryJobService.enqueue(owner, HistoryJobMode.FULL, target);
        }

        log.info("[DEV] Symbol normalization complete: portfoliosUpdated={}, equitiesNormalized={}, cachesEvicted={}, historyEnqueued={}",
                updatedPortfolios, normalizedEquities, cachesEvicted, ownersToRebuild.size());
        return ResponseEntity.ok(Map.of(
                "portfoliosUpdated", updatedPortfolios,
                "equitiesNormalized", normalizedEquities,
                "cachesEvicted", cachesEvicted,
                "historyEnqueued", ownersToRebuild.size()));
    }
}
