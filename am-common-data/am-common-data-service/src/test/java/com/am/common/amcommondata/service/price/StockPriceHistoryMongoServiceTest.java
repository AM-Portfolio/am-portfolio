package com.am.common.amcommondata.service.price;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Date;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.am.common.amcommondata.document.price.StockPriceHistoryDocument;
import com.am.common.amcommondata.repository.price.StockPriceHistoryMongoRepository;

@ExtendWith(MockitoExtension.class)
class StockPriceHistoryMongoServiceTest {

    @Mock
    private StockPriceHistoryMongoRepository historyRepo;

    @InjectMocks
    private StockPriceHistoryMongoService service;

    @Test
    void saveSessionTicksUpsertsExplicitTimestamps() {
        when(historyRepo.saveAll(anyList())).thenAnswer(inv -> inv.getArgument(0));

        long ts = 1_700_000_000_000L;
        int n = service.saveSessionTicks(List.of(
                StockPriceHistoryDocument.builder()
                        .symbol("RELIANCE")
                        .price(100.0)
                        .timestampMinute(ts)
                        .createdAt(new Date())
                        .build(),
                StockPriceHistoryDocument.builder()
                        .symbol("BAD")
                        .price(0.0)
                        .timestampMinute(ts)
                        .build()));

        assertEquals(1, n);
        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<StockPriceHistoryDocument>> cap =
                ArgumentCaptor.forClass(List.class);
        verify(historyRepo).saveAll(cap.capture());
        StockPriceHistoryDocument saved = cap.getValue().get(0);
        assertEquals("RELIANCE", saved.getSymbol());
        assertEquals("RELIANCE_" + saved.getTimestampMinute(), saved.getId());
        assertEquals(100.0, saved.getPrice());
    }

    @Test
    void saveSessionTicksNoOpOnEmpty() {
        assertEquals(0, service.saveSessionTicks(List.of()));
        assertEquals(0, service.saveSessionTicks(null));
        verify(historyRepo, never()).saveAll(anyList());
    }
}
