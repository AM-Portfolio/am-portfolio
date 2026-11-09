package com.portfolio.service.accounts;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.am.common.amcommondata.model.asset.equity.EquityModel;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.am.common.amcommondata.model.PortfolioModelV1;
import com.am.common.amcommondata.model.accounts.AccountsHubModel;
import com.am.common.amcommondata.model.accounts.AccountsHubModel.AccountsPortfolioRow;
import com.am.common.amcommondata.model.demat.DematAccountModel;
import com.am.common.amcommondata.model.enums.BrokerType;
import com.am.common.amcommondata.model.family.FamilyMember;
import com.am.common.amcommondata.model.nps.NpsDocumentMeta;
import com.am.common.amcommondata.service.PortfolioService;
import com.portfolio.service.cas.CasImportService;
import com.portfolio.service.family.FamilyMemberService;
import com.portfolio.service.nps.NpsImportService;

@ExtendWith(MockitoExtension.class)
class AccountsHubServiceTest {

    @Mock
    private CasImportService casImportService;
    @Mock
    private NpsImportService npsImportService;
    @Mock
    private PortfolioService portfolioService;
    @Mock
    private FamilyMemberService familyMemberService;

    @InjectMocks
    private AccountsHubService accountsHubService;

    @Test
    void hubForUser_composesIdentityStatsAndPortfolios() {
        AccountsHubService.invalidateXirrCache("u1");
        when(casImportService.accountsForOwnerAllPersonas("u1")).thenReturn(List.of(
                DematAccountModel.builder()
                        .accountNo("1208")
                        .accountType("demat")
                        .active(true)
                        .email("a@x.com")
                        .mobile("999")
                        .nominee("Spouse")
                        .nomineeRegistered(true)
                        .build(),
                DematAccountModel.builder()
                        .accountNo("F1")
                        .accountType("mf")
                        .active(true)
                        .build()));
        when(casImportService.latestMeta("u1")).thenReturn(Optional.empty());
        when(npsImportService.listMetasForOwner("u1")).thenReturn(List.of(
                NpsDocumentMeta.builder()
                        .pran("1101")
                        .subscriberName("Munish")
                        .subscriberEmail("nps@x.com")
                        .subscriberMobile("888")
                        .tier1Value(100.0)
                        .build()));
        when(portfolioService.getPortfoliosByUserId("u1")).thenReturn(List.of(
                PortfolioModelV1.builder()
                        .id(UUID.randomUUID())
                        .name("Dhan")
                        .brokerType(BrokerType.DHAN)
                        .totalValue(200.0)
                        .investmentAmount(150.0)
                        .assetCount(2)
                        .build()));
        when(familyMemberService.listForOwner("u1")).thenReturn(List.of());

        AccountsHubModel hub = accountsHubService.hubForUser("u1");
        assertNotNull(hub.getIdentity());
        assertEquals("Munish", hub.getIdentity().getName());
        assertEquals("nps@x.com", hub.getIdentity().getEmail());
        assertEquals("888", hub.getIdentity().getMobile());
        assertEquals("1101", hub.getIdentity().getPran());
        assertEquals(1, hub.getStats().getActiveDematCount());
        assertEquals(1, hub.getStats().getMfFolioCount());
        assertTrue(Boolean.TRUE.equals(hub.getStats().getNpsLinked()));
        assertEquals(1, hub.getPortfolios().size());
        assertEquals("Dhan", hub.getPortfolios().get(0).getDisplayName());
        assertEquals(50.0, hub.getPortfolios().get(0).getReturnAbs(), 0.001);
        assertEquals(2, hub.getDematAccounts().size());
        assertEquals("Spouse", hub.getDematAccounts().get(0).getNominee());
        assertTrue(Boolean.TRUE.equals(hub.getDematAccounts().get(0).getNomineeRegistered()));
        // Portfolio-level investmentAmount + totalValue synthesizes dated cashflows.
        assertNotNull(hub.getStats().getOverallXirrPct());
    }

    @Test
    void computeOverallXirr_usesAssetCostAndCreatedAt() {
        LocalDateTime bought = LocalDateTime.now().minusDays(30);
        EquityModel eq = EquityModel.builder()
                .symbol("RELIANCE")
                .quantity(10.0)
                .avgBuyingPrice(100.0)
                .investmentValue(1000.0)
                .currentValue(1200.0)
                .createdAt(bought) 
                
                .build();
        PortfolioModelV1 book = PortfolioModelV1.builder()
                .id(UUID.randomUUID())
                .name("Zerodha")
                .brokerType(BrokerType.ZERODHA)
                .totalValue(1200.0)
                .investmentAmount(1000.0)
                .equityModels(List.of(eq))
                .createdAt(bought)
                .build();
        Double xirr = AccountsHubService.computeOverallXirr(List.of(book));
        assertNotNull(xirr);
        assertTrue(xirr > 0);
    }

    @Test
    void computeOverallXirr_fallsBackWhenCreatedAtMissing() {
        EquityModel eq = EquityModel.builder()
                .symbol("INFY")
                .quantity(5.0)
                .avgBuyingPrice(200.0)
                .investmentValue(1000.0)
                .currentValue(1100.0)
                .build();
        PortfolioModelV1 book = PortfolioModelV1.builder()
                .id(UUID.randomUUID())
                .name("Groww")
                .brokerType(BrokerType.GROWW)
                .totalValue(1100.0)
                .investmentAmount(1000.0)
                .equityModels(List.of(eq))
                .build();
        assertNotNull(AccountsHubService.computeOverallXirr(List.of(book)));
    }

    @Test
    void computeOverallXirr_nullWhenNoCapital() {
        PortfolioModelV1 book = PortfolioModelV1.builder()
                .id(UUID.randomUUID())
                .name("Empty")
                .brokerType(BrokerType.ZERODHA)
                .totalValue(0.0)
                .build();
        assertNull(AccountsHubService.computeOverallXirr(List.of(book)));
    }

    @Test
    void computeOverallXirr_portfolioLevelWhenAssetsMissingButInvestedSet() {
        PortfolioModelV1 kotak = PortfolioModelV1.builder()
                .id(UUID.randomUUID())
                .name("Kotak")
                .brokerType(BrokerType.KOTAK)
                .familyMemberId("fm1")
                .totalValue(42339.65)
                .investmentAmount(0.0)
                .assetCount(13)
                .build();
        PortfolioModelV1 cdsl = PortfolioModelV1.builder()
                .id(UUID.randomUUID())
                .name("CDSL")
                .brokerType(BrokerType.CDSL)
                .familyMemberId("fm1")
                .totalValue(127188.22)
                .investmentAmount(111500.0)
                .assetCount(27)
                .build();
        assertNotNull(AccountsHubService.computeOverallXirr(List.of(cdsl)));
        assertNotNull(AccountsHubService.computeOverallXirr(List.of(kotak, cdsl)));
    }

    @Test
    void hubForUser_computesPersonaXirrForFamilyAndHousehold() {
        AccountsHubService.invalidateXirrCache("u1");
        LocalDateTime bought = LocalDateTime.now().minusDays(60);
        when(casImportService.accountsForOwnerAllPersonas("u1")).thenReturn(List.of());
        when(casImportService.latestMeta("u1")).thenReturn(Optional.empty());
        when(npsImportService.listMetasForOwner("u1")).thenReturn(List.of());
        when(portfolioService.getPortfoliosByUserId("u1")).thenReturn(List.of(
                PortfolioModelV1.builder()
                        .id(UUID.randomUUID())
                        .name("You Groww")
                        .brokerType(BrokerType.GROWW)
                        .totalValue(2000.0)
                        .investmentAmount(1500.0)
                        .equityModels(List.of(EquityModel.builder()
                                .symbol("TCS")
                                .quantity(1.0)
                                .investmentValue(1500.0)
                                .currentValue(2000.0)
                                .createdAt(bought)
                                .build()))
                        .createdAt(bought)
                        .build(),
                PortfolioModelV1.builder()
                        .id(UUID.randomUUID())
                        .name("Priyanshu Zerodha")
                        .brokerType(BrokerType.ZERODHA)
                        .familyMemberId("fm1")
                        .totalValue(10000.0)
                        .investmentAmount(8000.0)
                        .equityModels(List.of(EquityModel.builder()
                                .symbol("HDFCBANK")
                                .quantity(10.0)
                                .investmentValue(8000.0)
                                .currentValue(10000.0)
                                .createdAt(bought)
                                .build()))
                        .createdAt(bought)
                        .build()));
        when(familyMemberService.listForOwner("u1")).thenReturn(List.of(
                FamilyMember.builder().id("fm1").displayName("Priyanshu").build()));

        AccountsHubModel hub = accountsHubService.hubForUser("u1");
        assertNotNull(hub.getStats().getOverallXirrPct());
        assertNotNull(hub.getStats().getHouseholdXirrPct());
        assertEquals(1, hub.getFamilyMembers().size());
        assertNotNull(hub.getFamilyMembers().get(0).getXirrPct());
    }

    @Test
    void hubForUser_filtersCdslAggregatorBooks() {
        when(casImportService.accountsForOwnerAllPersonas("u1")).thenReturn(List.of());
        when(casImportService.latestMeta("u1")).thenReturn(Optional.empty());
        when(npsImportService.listMetasForOwner("u1")).thenReturn(List.of());
        when(portfolioService.getPortfoliosByUserId("u1")).thenReturn(List.of(
                PortfolioModelV1.builder()
                        .id(UUID.randomUUID())
                        .name("CDSL CAS")
                        .brokerType(BrokerType.CDSL)
                        .totalValue(10.0)
                        .build(),
                PortfolioModelV1.builder()
                        .id(UUID.randomUUID())
                        .name("Zerodha")
                        .brokerType(BrokerType.ZERODHA)
                        .totalValue(100.0)
                        .investmentAmount(80.0)
                        .assetCount(1)
                        .build()));
        when(familyMemberService.listForOwner("u1")).thenReturn(List.of());

        AccountsHubModel hub = accountsHubService.hubForUser("u1");
        assertEquals(1, hub.getPortfolios().size());
        assertEquals("Zerodha", hub.getPortfolios().get(0).getDisplayName());
    }

    @Test
    void hubForUser_portfolioCountUsesDematNotBrokerBooks() {
        when(casImportService.accountsForOwnerAllPersonas("u1")).thenReturn(List.of(
                DematAccountModel.builder()
                        .accountNo("D1")
                        .accountType("demat")
                        .familyMemberId("fm1")
                        .build(),
                DematAccountModel.builder()
                        .accountNo("D2")
                        .accountType("demat")
                        .familyMemberId("fm1")
                        .build(),
                DematAccountModel.builder()
                        .accountNo("D3")
                        .accountType("demat")
                        .familyMemberId("fm1")
                        .build(),
                DematAccountModel.builder()
                        .accountNo("F1")
                        .accountType("mf")
                        .familyMemberId("fm1")
                        .build()));
        when(casImportService.latestMeta("u1")).thenReturn(Optional.empty());
        when(npsImportService.listMetasForOwner("u1")).thenReturn(List.of());
        when(portfolioService.getPortfoliosByUserId("u1")).thenReturn(List.of(
                PortfolioModelV1.builder()
                        .id(UUID.randomUUID())
                        .name("Zerodha")
                        .brokerType(BrokerType.ZERODHA)
                        .familyMemberId("fm1")
                        .totalValue(900000.0)
                        .build(),
                PortfolioModelV1.builder()
                        .id(UUID.randomUUID())
                        .name("Mirae")
                        .brokerType(BrokerType.CAS_DEMAT)
                        .dematBoid("1200000000000001")
                        .familyMemberId("fm1")
                        .totalValue(10.0)
                        .build(),
                PortfolioModelV1.builder()
                        .id(UUID.randomUUID())
                        .name("Indstocks")
                        .brokerType(BrokerType.CAS_DEMAT)
                        .dematBoid("1200000000000002")
                        .familyMemberId("fm1")
                        .totalValue(20.0)
                        .build(),
                PortfolioModelV1.builder()
                        .id(UUID.randomUUID())
                        .name("Mutual Funds")
                        .brokerType(BrokerType.CAS_DEMAT)
                        .dematBoid("mf:cas")
                        .familyMemberId("fm1")
                        .totalValue(30.0)
                        .build()));
        when(familyMemberService.listForOwner("u1")).thenReturn(List.of(
                FamilyMember.builder()
                        .id("fm1")
                        .displayName("Priyanshu Kumar")
                        .email("priyanshukpd@gmail.com")
                        .source("CAS")
                        .build()));

        AccountsHubModel hub = accountsHubService.hubForUser("u1");
        assertEquals(4, hub.getPortfolios().size());
        assertEquals(1, hub.getFamilyMembers().size());
        // Directory demat rows for fm1 (D1,D2,D3) — not CAS_DEMAT book count.
        assertEquals(3, hub.getFamilyMembers().get(0).getPortfolioCount());
    }

    @Test
    void hubForUser_computesHouseholdFamilyShare() {
        when(casImportService.accountsForOwnerAllPersonas("u1")).thenReturn(List.of(
                DematAccountModel.builder()
                        .accountNo("P1")
                        .accountType("demat")
                        .holdingValue(100.0)
                        .build(),
                DematAccountModel.builder()
                        .accountNo("F1")
                        .accountType("demat")
                        .familyMemberId("fm1")
                        .holdingValue(300.0)
                        .build()));
        when(casImportService.latestMeta("u1")).thenReturn(Optional.empty());
        when(npsImportService.listMetasForOwner("u1")).thenReturn(List.of(
                NpsDocumentMeta.builder().pran("1").totalValue(100.0).build(),
                NpsDocumentMeta.builder()
                        .pran("2")
                        .familyMemberId("fm1")
                        .totalValue(100.0)
                        .build()));
        when(portfolioService.getPortfoliosByUserId("u1")).thenReturn(List.of(
                PortfolioModelV1.builder()
                        .id(UUID.randomUUID())
                        .name("Zerodha")
                        .brokerType(BrokerType.ZERODHA)
                        .totalValue(200.0)
                        .build(),
                PortfolioModelV1.builder()
                        .id(UUID.randomUUID())
                        .name("Zerodha")
                        .brokerType(BrokerType.ZERODHA)
                        .familyMemberId("fm1")
                        .totalValue(200.0)
                        .build()));
        when(familyMemberService.listForOwner("u1")).thenReturn(List.of(
                FamilyMember.builder().id("fm1").displayName("Priyanshu").build()));

        AccountsHubModel hub = accountsHubService.hubForUser("u1");
        // Books + unmatched directory residual + NPS (no longer drop directory once books > 0).
        assertEquals(400.0, hub.getStats().getPrimaryTotalValue(), 0.01);
        assertEquals(600.0, hub.getStats().getFamilyTotalValue(), 0.01);
        assertEquals(1000.0, hub.getStats().getHouseholdTotalValue(), 0.01);
        assertEquals(60.0, hub.getStats().getFamilySharePct(), 0.01);
        assertEquals(1, hub.getFamilyMembers().size());
        assertEquals(600.0, hub.getFamilyMembers().get(0).getTotalValue(), 0.01);
    }

    @Test
    void sumValueForMemberId_sumsTaggedDematBrokerAndNps() {
        var demat = List.of(
                DematAccountModel.builder()
                        .accountNo("D1")
                        .familyMemberId("fm1")
                        .holdingValue(100.0)
                        .build(),
                DematAccountModel.builder()
                        .accountNo("P1")
                        .holdingValue(50.0)
                        .build());
        var rows = List.of(
                AccountsPortfolioRow.builder()
                        .displayName("Z")
                        .familyMemberId("fm1")
                        .current(200.0)
                        .brokerType("ZERODHA")
                        .build());
        var nps = List.of(
                NpsDocumentMeta.builder()
                        .pran("2")
                        .familyMemberId("fm1")
                        .totalValue(25.0)
                        .build());

        // Zerodha 200 + unmatched demat residual 100 + NPS 25.
        assertEquals(
                325.0,
                AccountsHubService.sumValueForMemberId(demat, rows, nps, "fm1"),
                0.01);
        var zeroBooks = List.of(
                AccountsPortfolioRow.builder()
                        .displayName("Mirae")
                        .familyMemberId("fm1")
                        .current(0.0)
                        .brokerType("CAS_DEMAT")
                        .build());
        assertEquals(
                125.0,
                AccountsHubService.sumValueForMemberId(demat, zeroBooks, nps, "fm1"),
                0.01);
        assertEquals(
                0.0,
                AccountsHubService.sumValueForMemberId(demat, rows, nps, "other"),
                0.01);
    }

    @Test
    void sumValueForMemberId_usesMaxBookAndDirectoryPlusResidual() {
        var demat = List.of(
                DematAccountModel.builder()
                        .accountNo("Z1")
                        .familyMemberId("fm1")
                        .accountType("demat")
                        .mappedBroker("zerodha")
                        .dpName("ZERODHA BROKING LIMITED")
                        .holdingValue(942866.0)
                        .build(),
                DematAccountModel.builder()
                        .accountNo("G1")
                        .familyMemberId("fm1")
                        .accountType("demat")
                        .mappedBroker("groww")
                        .dpName("GROWW INVEST TECH PRIVATE LIMITED")
                        .holdingValue(106711.0)
                        .build(),
                DematAccountModel.builder()
                        .accountNo("MF1")
                        .familyMemberId("fm1")
                        .accountType("mf")
                        .holdingValue(127188.0)
                        .build());
        var rows = List.of(
                AccountsPortfolioRow.builder()
                        .displayName("Zerodha")
                        .familyMemberId("fm1")
                        .brokerType("ZERODHA")
                        .current(930972.0)
                        .build(),
                AccountsPortfolioRow.builder()
                        .displayName("Mutual Funds")
                        .familyMemberId("fm1")
                        .brokerType("CAS_DEMAT")
                        .dematBoid("mf:cas")
                        .current(127188.0)
                        .build());

        // max(Zerodha book, dir) + Groww residual + MF (matched, not double-counted).
        assertEquals(
                942866.0 + 106711.0 + 127188.0,
                AccountsHubService.sumValueForMemberId(demat, rows, List.of(), "fm1"),
                0.01);

        var allBooks = List.of(
                rows.get(0),
                rows.get(1),
                AccountsPortfolioRow.builder()
                        .displayName("Groww")
                        .familyMemberId("fm1")
                        .brokerType("GROWW")
                        .current(106711.0)
                        .build());
        assertEquals(
                942866.0 + 106711.0 + 127188.0,
                AccountsHubService.sumValueForMemberId(demat, allBooks, List.of(), "fm1"),
                0.01);
    }
}
