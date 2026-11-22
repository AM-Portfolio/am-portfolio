package com.portfolio.analytics.intelligence;

import com.am.common.amcommondata.model.PortfolioModelV1;
import com.am.common.amcommondata.repository.family.FamilyMemberRepository;
import com.am.common.amcommondata.service.PortfolioService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AggregatePortfolioLoaderTest {

    @Mock
    private PortfolioService portfolioService;

    @Mock
    private AggregatePortfolioFactory aggregatePortfolioFactory;

    @Mock
    private FamilyMemberRepository familyMemberRepository;

    private AggregatePortfolioLoader loader;

    @BeforeEach
    void setUp() {
        loader = new AggregatePortfolioLoader(portfolioService, aggregatePortfolioFactory, familyMemberRepository);
    }

    @Test
    void loadMerged_singleBookSkipsMergeAndCaches() {
        String userId = "user-1";
        PortfolioModelV1 book = new PortfolioModelV1();
        book.setId(UUID.randomUUID());
        book.setTotalValue(930_000.0);
        when(portfolioService.getPortfoliosByUserId(userId)).thenReturn(List.of(book));

        PortfolioModelV1 first = loader.loadMerged(userId);
        PortfolioModelV1 second = loader.loadMerged(userId);

        assertThat(first).isSameAs(book);
        assertThat(second).isSameAs(book);
        assertThat(first.getTotalValue()).isEqualTo(930_000.0);
        verify(portfolioService, times(1)).getPortfoliosByUserId(userId);
        verify(aggregatePortfolioFactory, never()).mergeBrokerBooks(anyString(), anyList());
    }

    @Test
    void loadMerged_mergesMultipleBooksAndCaches() {
        String userId = "user-3";
        PortfolioModelV1 firstBook = new PortfolioModelV1();
        firstBook.setId(UUID.randomUUID());
        PortfolioModelV1 secondBook = new PortfolioModelV1();
        secondBook.setId(UUID.randomUUID());
        PortfolioModelV1 merged = new PortfolioModelV1();
        merged.setTotalValue(1_500.0);
        when(portfolioService.getPortfoliosByUserId(userId)).thenReturn(List.of(firstBook, secondBook));
        when(aggregatePortfolioFactory.mergeBrokerBooks(eq(userId), anyList())).thenReturn(merged);

        assertThat(loader.loadMerged(userId)).isSameAs(merged);
        assertThat(loader.loadMerged(userId)).isSameAs(merged);
        verify(aggregatePortfolioFactory, times(1)).mergeBrokerBooks(eq(userId), anyList());
    }

    @Test
    void evict_forcesReload() {
        String userId = "user-2";
        PortfolioModelV1 book = new PortfolioModelV1();
        book.setId(UUID.randomUUID());
        when(portfolioService.getPortfoliosByUserId(userId)).thenReturn(List.of(book));

        loader.loadMerged(userId);
        loader.evict(userId);
        loader.loadMerged(userId);

        verify(portfolioService, times(2)).getPortfoliosByUserId(userId);
    }
}
