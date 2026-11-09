package com.portfolio.service;

import com.am.common.amcommondata.model.PortfolioModelV1;
import com.am.common.amcommondata.model.enums.BrokerType;
import com.am.common.amcommondata.model.enums.PortfolioKind;
import com.am.common.amcommondata.service.PortfolioService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Collections;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class NewUserPortfolioFallbackServiceTest {

    private static final String TEMPLATE_ID = "aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa";
    private static final UUID TEMPLATE_UUID = UUID.fromString(TEMPLATE_ID);
    private static final String USER = "user-1";

    @Mock private PortfolioService portfolioService;
    @Mock private StringRedisTemplate redisTemplate;
    @InjectMocks private NewUserPortfolioFallbackService service;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(service, "demoPortfolioId", TEMPLATE_ID);
    }

    @Test
    void paperOnly_doesNotHideDemo_clonesPersonal() {
        UUID personalId = UUID.fromString("bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb");
        PortfolioModelV1 paper = PortfolioModelV1.builder()
                .id(UUID.randomUUID())
                .owner(USER)
                .name("Paper")
                .portfolioKind(PortfolioKind.PAPER)
                .brokerType(BrokerType.ZERODHA)
                .build();
        PortfolioModelV1 template = PortfolioModelV1.builder()
                .id(TEMPLATE_UUID)
                .owner("seed-owner")
                .name("Template")
                .totalValue(1000.0)
                .build();
        PortfolioModelV1 saved = PortfolioModelV1.builder()
                .id(personalId)
                .owner(USER)
                .name("Demo Portfolio")
                .portfolioKind(PortfolioKind.DEMO)
                .totalValue(1000.0)
                .build();

        when(portfolioService.getPortfoliosByUserId(USER)).thenReturn(List.of(paper));
        when(portfolioService.getPortfolioById(TEMPLATE_UUID)).thenReturn(template);
        when(portfolioService.createPortfolio(any())).thenReturn(saved);

        PortfolioModelV1 demo = service.getDemoPortfolioForNewUser(USER, List.of());

        assertThat(demo).isNotNull();
        assertThat(demo.getId()).isEqualTo(personalId);
        assertThat(demo.getOwner()).isEqualTo(USER);
        assertThat(demo.getPortfolioKind()).isEqualTo(PortfolioKind.DEMO);

        ArgumentCaptor<PortfolioModelV1> captor = ArgumentCaptor.forClass(PortfolioModelV1.class);
        verify(portfolioService).createPortfolio(captor.capture());
        assertThat(captor.getValue().getPortfolioKind()).isEqualTo(PortfolioKind.DEMO);
        assertThat(captor.getValue().getOwner()).isEqualTo(USER);
        assertThat(captor.getValue().getId()).isNotEqualTo(TEMPLATE_UUID);
    }

    @Test
    void realBroker_softHidesPersonalDemo() {
        UUID demoId = UUID.fromString("cccccccc-cccc-cccc-cccc-cccccccccccc");
        PortfolioModelV1 demo = PortfolioModelV1.builder()
                .id(demoId)
                .owner(USER)
                .portfolioKind(PortfolioKind.DEMO)
                .build();
        PortfolioModelV1 broker = PortfolioModelV1.builder()
                .id(UUID.randomUUID())
                .owner(USER)
                .portfolioKind(PortfolioKind.BROKER)
                .brokerType(BrokerType.ZERODHA)
                .build();

        when(portfolioService.getPortfoliosByUserId(USER)).thenReturn(List.of(demo, broker));

        assertThat(service.getDemoPortfolioForNewUser(USER, List.of())).isNull();
        verify(portfolioService).softDeletePortfolio(demoId.toString(), USER);
        verify(portfolioService, never()).createPortfolio(any());
    }

    @Test
    void afterBrokerGone_reClonesDemo() {
        UUID personalId = UUID.fromString("dddddddd-dddd-dddd-dddd-dddddddddddd");
        PortfolioModelV1 template = PortfolioModelV1.builder()
                .id(TEMPLATE_UUID)
                .owner("seed-owner")
                .totalValue(50.0)
                .build();
        PortfolioModelV1 saved = PortfolioModelV1.builder()
                .id(personalId)
                .owner(USER)
                .portfolioKind(PortfolioKind.DEMO)
                .build();

        when(portfolioService.getPortfoliosByUserId(USER)).thenReturn(Collections.emptyList());
        when(portfolioService.getPortfolioById(TEMPLATE_UUID)).thenReturn(template);
        when(portfolioService.createPortfolio(any())).thenReturn(saved);

        PortfolioModelV1 demo = service.ensurePersonalDemoLifecycle(USER);

        assertThat(demo).isNotNull();
        assertThat(demo.getId()).isEqualTo(personalId);
        verify(portfolioService).createPortfolio(any());
    }

    @Test
    void resolveRequest_keepsUserAsOwner_rewritesTemplateIdToPersonal() {
        UUID personalId = UUID.fromString("eeeeeeee-eeee-eeee-eeee-eeeeeeeeeeee");
        PortfolioModelV1 existing = PortfolioModelV1.builder()
                .id(personalId)
                .owner(USER)
                .portfolioKind(PortfolioKind.DEMO)
                .build();
        when(portfolioService.getPortfoliosByUserId(USER)).thenReturn(List.of(existing));

        NewUserPortfolioFallbackService.DemoResolution res =
                service.resolveRequest(USER, TEMPLATE_ID);

        assertThat(res.userId()).isEqualTo(USER);
        assertThat(res.portfolioId()).isEqualTo(personalId.toString());
        verify(portfolioService, never()).createPortfolio(any());
    }

    @Test
    void listBasicPortfolios_includesDemoAndPaper() {
        UUID demoId = UUID.fromString("ffffffff-ffff-ffff-ffff-ffffffffffff");
        PortfolioModelV1 demo = PortfolioModelV1.builder()
                .id(demoId)
                .owner(USER)
                .name("Demo Portfolio")
                .portfolioKind(PortfolioKind.DEMO)
                .build();
        PortfolioModelV1 paper = PortfolioModelV1.builder()
                .id(UUID.randomUUID())
                .owner(USER)
                .name("Paper")
                .portfolioKind(PortfolioKind.PAPER)
                .build();
        when(portfolioService.getPortfoliosByUserId(USER)).thenReturn(List.of(demo, paper));

        List<NewUserPortfolioFallbackService.BasicPortfolioRow> rows = service.listBasicPortfolios(USER);

        assertThat(rows).hasSize(2);
        assertThat(rows.stream().anyMatch(r -> r.dummy() && "DEMO".equals(r.kind()))).isTrue();
        assertThat(rows.stream().anyMatch(r -> "PAPER".equals(r.kind()) && !r.dummy())).isTrue();
    }

    @Test
    void listBasicPortfolios_includesFamilyMemberFields() {
        PortfolioModelV1 primary = PortfolioModelV1.builder()
                .id(UUID.randomUUID())
                .owner(USER)
                .name("Dhan")
                .portfolioKind(PortfolioKind.BROKER)
                .brokerType(BrokerType.DHAN)
                .build();
        PortfolioModelV1 family = PortfolioModelV1.builder()
                .id(UUID.randomUUID())
                .owner(USER)
                .name("Dhan")
                .portfolioKind(PortfolioKind.BROKER)
                .brokerType(BrokerType.DHAN)
                .familyMemberId("fm-priyanshu")
                .familyMemberName("Priyanshu Kumar")
                .build();
        when(portfolioService.getPortfoliosByUserId(USER)).thenReturn(List.of(primary, family));

        List<NewUserPortfolioFallbackService.BasicPortfolioRow> rows = service.listBasicPortfolios(USER);

        assertThat(rows).hasSize(2);
        NewUserPortfolioFallbackService.BasicPortfolioRow primaryRow = rows.stream()
                .filter(r -> r.familyMemberId() == null)
                .findFirst()
                .orElseThrow();
        NewUserPortfolioFallbackService.BasicPortfolioRow familyRow = rows.stream()
                .filter(r -> "fm-priyanshu".equals(r.familyMemberId()))
                .findFirst()
                .orElseThrow();
        assertThat(primaryRow.familyMemberName()).isNull();
        assertThat(familyRow.familyMemberName()).isEqualTo("Priyanshu Kumar");
        assertThat(familyRow.brokerType()).isEqualTo("DHAN");
    }

    @Test
    void hasRealBrokerBook_ignoresPaperBasketDemoAndCdsl() {
        assertThat(NewUserPortfolioFallbackService.hasRealBrokerBook(List.of(
                PortfolioModelV1.builder().portfolioKind(PortfolioKind.PAPER).brokerType(BrokerType.ZERODHA).build(),
                PortfolioModelV1.builder().portfolioKind(PortfolioKind.BASKET).brokerType(BrokerType.ZERODHA).build(),
                PortfolioModelV1.builder().portfolioKind(PortfolioKind.DEMO).build()
        ))).isFalse();

        assertThat(NewUserPortfolioFallbackService.hasRealBrokerBook(List.of(
                PortfolioModelV1.builder().portfolioKind(PortfolioKind.BROKER).brokerType(BrokerType.CDSL).build()
        ))).isFalse();

        assertThat(NewUserPortfolioFallbackService.hasRealBrokerBook(List.of(
                PortfolioModelV1.builder().portfolioKind(PortfolioKind.BROKER).brokerType(BrokerType.GROWW).build()
        ))).isTrue();

        assertThat(NewUserPortfolioFallbackService.hasRealBrokerBook(List.of(
                PortfolioModelV1.builder().portfolioKind(null).brokerType(BrokerType.ZERODHA).build()
        ))).isTrue();

        assertThat(NewUserPortfolioFallbackService.hasRealBrokerBook(List.of(
                PortfolioModelV1.builder().portfolioKind(PortfolioKind.BROKER).brokerType(null).build()
        ))).isFalse();
    }

    @Test
    void listBasicPortfolios_cdslOnly_includesCdslAndKeepsDemo() {
        UUID demoId = UUID.fromString("aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee");
        UUID cdslId = UUID.fromString("11111111-2222-3333-4444-555555555555");
        PortfolioModelV1 demo = PortfolioModelV1.builder()
                .id(demoId)
                .owner(USER)
                .name("Demo Portfolio")
                .portfolioKind(PortfolioKind.DEMO)
                .build();
        PortfolioModelV1 cdsl = PortfolioModelV1.builder()
                .id(cdslId)
                .owner(USER)
                .name("CDSL")
                .portfolioKind(PortfolioKind.BROKER)
                .brokerType(BrokerType.CDSL)
                .assetCount(2)
                .build();
        when(portfolioService.getPortfoliosByUserId(USER)).thenReturn(List.of(demo, cdsl));

        List<NewUserPortfolioFallbackService.BasicPortfolioRow> rows = service.listBasicPortfolios(USER);

        assertThat(rows).hasSize(2);
        assertThat(rows.stream().anyMatch(r -> r.dummy() && "DEMO".equals(r.kind()))).isTrue();
        NewUserPortfolioFallbackService.BasicPortfolioRow cdslRow = rows.stream()
                .filter(r -> "CDSL".equals(r.brokerType()))
                .findFirst()
                .orElseThrow();
        assertThat(cdslRow.portfolioName()).isEqualTo("CDSL CAS");
        assertThat(cdslRow.portfolioId()).isEqualTo(cdslId.toString());
        verify(portfolioService, never()).softDeletePortfolio(any(), any());
    }

    @Test
    void listBasicPortfolios_showsCasDematShortNames_hidesCdsl() {
        UUID growwId = UUID.fromString("12121212-3434-5656-7878-909090909091");
        UUID miraeId = UUID.fromString("22222222-3333-4444-5555-666666666666");
        UUID finId = UUID.fromString("33333333-4444-5555-6666-777777777777");
        UUID cdslId = UUID.fromString("99999999-8888-7777-6666-555555555556");
        PortfolioModelV1 groww = PortfolioModelV1.builder()
                .id(growwId)
                .owner(USER)
                .name("Groww")
                .portfolioKind(PortfolioKind.BROKER)
                .brokerType(BrokerType.GROWW)
                .assetCount(5)
                .build();
        PortfolioModelV1 mirae = PortfolioModelV1.builder()
                .id(miraeId)
                .owner(USER)
                .name("Mirae")
                .portfolioKind(PortfolioKind.BROKER)
                .brokerType(BrokerType.CAS_DEMAT)
                .dematBoid("boid-mirae")
                .assetCount(1)
                .build();
        PortfolioModelV1 fin = PortfolioModelV1.builder()
                .id(finId)
                .owner(USER)
                .name("Finwizard")
                .portfolioKind(PortfolioKind.BROKER)
                .brokerType(BrokerType.CAS_DEMAT)
                .dematBoid("boid-fin")
                .assetCount(1)
                .build();
        PortfolioModelV1 cdsl = PortfolioModelV1.builder()
                .id(cdslId)
                .owner(USER)
                .name("CDSL CAS")
                .portfolioKind(PortfolioKind.BROKER)
                .brokerType(BrokerType.CDSL)
                .build();
        when(portfolioService.getPortfoliosByUserId(USER)).thenReturn(List.of(groww, mirae, fin, cdsl));

        List<NewUserPortfolioFallbackService.BasicPortfolioRow> rows = service.listBasicPortfolios(USER);

        assertThat(rows).extracting(NewUserPortfolioFallbackService.BasicPortfolioRow::portfolioName)
                .containsExactlyInAnyOrder("Groww", "Mirae", "Finwizard");
        assertThat(rows).noneMatch(r -> "CDSL".equals(r.brokerType()));
    }

    @Test
    void listBasicPortfolios_mutualFundsNotHiddenAsCdsl() {
        assertThat(NewUserPortfolioFallbackService.isCdslListRow(
                PortfolioModelV1.builder()
                        .name("Mutual Funds")
                        .brokerType(BrokerType.CAS_DEMAT)
                        .portfolioKind(PortfolioKind.BROKER)
                        .build())).isFalse();
    }

    @Test
    void listBasicPortfolios_cdslPlusGroww_hidesCdslAndSoftHidesDemo() {
        UUID demoId = UUID.fromString("cccccccc-dddd-eeee-ffff-000000000000");
        UUID cdslId = UUID.fromString("99999999-8888-7777-6666-555555555555");
        UUID growwId = UUID.fromString("12121212-3434-5656-7878-909090909090");
        PortfolioModelV1 demo = PortfolioModelV1.builder()
                .id(demoId)
                .owner(USER)
                .portfolioKind(PortfolioKind.DEMO)
                .build();
        PortfolioModelV1 cdsl = PortfolioModelV1.builder()
                .id(cdslId)
                .owner(USER)
                .name("CDSL")
                .portfolioKind(PortfolioKind.BROKER)
                .brokerType(BrokerType.CDSL)
                .build();
        PortfolioModelV1 groww = PortfolioModelV1.builder()
                .id(growwId)
                .owner(USER)
                .name("Groww")
                .portfolioKind(PortfolioKind.BROKER)
                .brokerType(BrokerType.GROWW)
                .assetCount(3)
                .build();
        // Lifecycle sees Demo+CDSL+Groww; after soft-hide, list reload omits DELETED Demo.
        when(portfolioService.getPortfoliosByUserId(USER))
                .thenReturn(List.of(demo, cdsl, groww), List.of(cdsl, groww));

        List<NewUserPortfolioFallbackService.BasicPortfolioRow> rows = service.listBasicPortfolios(USER);

        assertThat(rows).hasSize(1);
        assertThat(rows.get(0).brokerType()).isEqualTo("GROWW");
        assertThat(rows.get(0).portfolioId()).isEqualTo(growwId.toString());
        verify(portfolioService).softDeletePortfolio(demoId.toString(), USER);
    }

    @Test
    void cdslOnly_doesNotSoftHideDemo() {
        UUID demoId = UUID.fromString("abababab-abab-abab-abab-abababababab");
        PortfolioModelV1 demo = PortfolioModelV1.builder()
                .id(demoId)
                .owner(USER)
                .portfolioKind(PortfolioKind.DEMO)
                .build();
        PortfolioModelV1 cdsl = PortfolioModelV1.builder()
                .id(UUID.randomUUID())
                .owner(USER)
                .portfolioKind(PortfolioKind.BROKER)
                .brokerType(BrokerType.CDSL)
                .build();
        when(portfolioService.getPortfoliosByUserId(USER)).thenReturn(List.of(demo, cdsl));

        PortfolioModelV1 result = service.ensurePersonalDemoLifecycle(USER);

        assertThat(result).isNotNull();
        assertThat(result.getId()).isEqualTo(demoId);
        verify(portfolioService, never()).softDeletePortfolio(any(), any());
        verify(portfolioService, never()).createPortfolio(any());
    }

    @Test
    void multipleDemos_softHidesExtras_keepsNewest() {
        UUID older = UUID.fromString("aaaaaaaa-1111-1111-1111-111111111111");
        UUID newer = UUID.fromString("bbbbbbbb-2222-2222-2222-222222222222");
        PortfolioModelV1 dOld = PortfolioModelV1.builder()
                .id(older)
                .owner(USER)
                .portfolioKind(PortfolioKind.DEMO)
                .updatedAt(java.time.LocalDateTime.of(2026, 1, 1, 0, 0))
                .build();
        PortfolioModelV1 dNew = PortfolioModelV1.builder()
                .id(newer)
                .owner(USER)
                .portfolioKind(PortfolioKind.DEMO)
                .updatedAt(java.time.LocalDateTime.of(2026, 11, 1, 0, 0))
                .build();
        when(portfolioService.getPortfoliosByUserId(USER)).thenReturn(List.of(dOld, dNew));

        PortfolioModelV1 result = service.ensurePersonalDemoLifecycle(USER);

        assertThat(result.getId()).isEqualTo(newer);
        verify(portfolioService).softDeletePortfolio(older.toString(), USER);
        verify(portfolioService, never()).createPortfolio(any());
    }

    @Test
    void usersGetIsolatedDemoIds() {
        UUID user1Demo = UUID.fromString("11111111-1111-1111-1111-111111111111");
        UUID user2Demo = UUID.fromString("22222222-2222-2222-2222-222222222222");
        PortfolioModelV1 template = PortfolioModelV1.builder().id(TEMPLATE_UUID).owner("seed").build();

        when(portfolioService.getPortfoliosByUserId("u1")).thenReturn(Collections.emptyList());
        when(portfolioService.getPortfoliosByUserId("u2")).thenReturn(Collections.emptyList());
        when(portfolioService.getPortfolioById(TEMPLATE_UUID)).thenReturn(template);
        when(portfolioService.createPortfolio(any())).thenAnswer(inv -> {
            PortfolioModelV1 in = inv.getArgument(0);
            return PortfolioModelV1.builder()
                    .id("u1".equals(in.getOwner()) ? user1Demo : user2Demo)
                    .owner(in.getOwner())
                    .portfolioKind(PortfolioKind.DEMO)
                    .build();
        });

        PortfolioModelV1 d1 = service.ensurePersonalDemoLifecycle("u1");
        PortfolioModelV1 d2 = service.ensurePersonalDemoLifecycle("u2");

        assertThat(d1.getId()).isNotEqualTo(d2.getId());
        assertThat(d1.getOwner()).isEqualTo("u1");
        assertThat(d2.getOwner()).isEqualTo("u2");
    }
}
