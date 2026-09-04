package com.portfolio.service.basket;

import com.am.common.amcommondata.document.basket.BasketDraftDocument;
import com.am.common.amcommondata.repository.basket.BasketDraftRepository;
import com.portfolio.service.basket.dto.BasketDraftDtos.UpsertBasketDraftRequest;
import com.portfolio.service.basket.exception.DraftLimitReachedException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BasketDraftServiceTest {

    @Mock
    private BasketDraftRepository basketDraftRepository;

    @InjectMocks
    private BasketDraftService basketDraftService;

    private UpsertBasketDraftRequest baseRequest;

    @BeforeEach
    void setUp() {
        baseRequest = UpsertBasketDraftRequest.builder()
                .userId("user-1")
                .sourcePortfolioId("portfolio-1")
                .etfIsin("INF123")
                .etfName("Test ETF")
                .basketName("My Basket")
                .investmentAmount(100000.0)
                .replicaScore(85.0)
                .hasCalculated(true)
                .excludedSymbols(Collections.emptyList())
                .manualQtyOverrides(Map.of("HDFCBANK", 3))
                .opportunity(Map.of("etfIsin", "INF123"))
                .build();
    }

    @Test
    void upsert_newDraft_whenUnderCap_saves() {
        when(basketDraftRepository.findByUserIdAndSourcePortfolioIdAndEtfIsin(
                "user-1", "portfolio-1", "INF123")).thenReturn(Optional.empty());
        when(basketDraftRepository.countByUserId("user-1")).thenReturn(2L, 3L);
        when(basketDraftRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        var result = basketDraftService.upsert(baseRequest);

        assertThat(result.getEtfIsin()).isEqualTo("INF123");
        assertThat(result.getId()).isNotBlank();
        ArgumentCaptor<BasketDraftDocument> captor = ArgumentCaptor.forClass(BasketDraftDocument.class);
        verify(basketDraftRepository).save(captor.capture());
        assertThat(captor.getValue().getManualQtyOverrides()).containsEntry("HDFCBANK", 3);
    }

    @Test
    void upsert_newDraft_whenAtCap_throwsDraftLimit() {
        when(basketDraftRepository.findByUserIdAndSourcePortfolioIdAndEtfIsin(
                "user-1", "portfolio-1", "INF123")).thenReturn(Optional.empty());
        when(basketDraftRepository.countByUserId("user-1")).thenReturn(5L);

        assertThatThrownBy(() -> basketDraftService.upsert(baseRequest))
                .isInstanceOf(DraftLimitReachedException.class);
        verify(basketDraftRepository, never()).save(any());
    }

    @Test
    void upsert_existingByKey_updatesWithoutCapCheck() {
        BasketDraftDocument existing = BasketDraftDocument.builder()
                .id("draft-1")
                .userId("user-1")
                .sourcePortfolioId("portfolio-1")
                .etfIsin("INF123")
                .createdAt(LocalDateTime.now().minusDays(1))
                .build();
        when(basketDraftRepository.findByUserIdAndSourcePortfolioIdAndEtfIsin(
                "user-1", "portfolio-1", "INF123")).thenReturn(Optional.of(existing));
        when(basketDraftRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        var result = basketDraftService.upsert(baseRequest);

        assertThat(result.getId()).isEqualTo("draft-1");
        verify(basketDraftRepository, never()).countByUserId(any());
    }

    @Test
    void getDraft_otherUser_notFound() {
        when(basketDraftRepository.findByIdAndUserId("draft-1", "user-2"))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> basketDraftService.getDraft("draft-1", "user-2"))
                .isInstanceOf(ResponseStatusException.class);
    }

    @Test
    void deleteAfterCreate_byDraftIdAndTripleKey() {
        basketDraftService.deleteAfterCreate("user-1", "draft-1", "portfolio-1", "INF123");

        verify(basketDraftRepository).deleteByIdAndUserId("draft-1", "user-1");
        verify(basketDraftRepository).deleteByUserIdAndSourcePortfolioIdAndEtfIsin(
                eq("user-1"), eq("portfolio-1"), eq("INF123"));
    }
}
