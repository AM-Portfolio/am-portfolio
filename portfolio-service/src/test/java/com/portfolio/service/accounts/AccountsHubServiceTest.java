package com.portfolio.service.accounts;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.am.common.amcommondata.model.PortfolioModelV1;
import com.am.common.amcommondata.model.accounts.AccountsHubModel;
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
        assertTrue(hub.getStats().getOverallXirrPct() == null);
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
                        .build()));
        when(familyMemberService.listForOwner("u1")).thenReturn(List.of(
                FamilyMember.builder()
                        .id("fm1")
                        .displayName("Priyanshu Kumar")
                        .email("priyanshukpd@gmail.com")
                        .source("CAS")
                        .build()));

        AccountsHubModel hub = accountsHubService.hubForUser("u1");
        assertEquals(1, hub.getPortfolios().size());
        assertEquals(1, hub.getFamilyMembers().size());
        assertEquals(3, hub.getFamilyMembers().get(0).getPortfolioCount());
    }
}
