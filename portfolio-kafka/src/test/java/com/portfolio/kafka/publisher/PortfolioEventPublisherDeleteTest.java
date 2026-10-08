package com.portfolio.kafka.publisher;

import com.portfolio.kafka.producer.KafkaProducerService;
import com.portfolio.model.events.PortfolioUpdateEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class PortfolioEventPublisherDeleteTest {

    @Mock
    private KafkaProducerService kafkaProducerService;

    private PortfolioEventPublisher publisher;

    @BeforeEach
    void setUp() {
        publisher = new PortfolioEventPublisher();
        ReflectionTestUtils.setField(publisher, "kafkaProducerService", kafkaProducerService);
    }

    @Test
    void publishPortfolioDelete_usesProvidedUuid_notRandom() {
        String owner = "owner-" + UUID.randomUUID();
        String portfolioId = UUID.randomUUID().toString();
        String name = "Name-" + UUID.randomUUID().toString().substring(0, 8);

        publisher.publishPortfolioDelete(owner, portfolioId, name, "TEST_SOURCE");

        ArgumentCaptor<PortfolioUpdateEvent> cap = ArgumentCaptor.forClass(PortfolioUpdateEvent.class);
        verify(kafkaProducerService).sendMessage(cap.capture(), isNull());
        PortfolioUpdateEvent event = cap.getValue();
        assertEquals("DELETE", event.getAction());
        assertEquals(owner, event.getUserId());
        assertEquals(portfolioId, event.getPortfolioId());
        assertEquals(UUID.fromString(portfolioId), event.getId());
        assertEquals(name, event.getName());
    }

    @Test
    void publishPortfolioDelete_skipsNonUuid() {
        publisher.publishPortfolioDelete("owner-1", "not-a-uuid", "x", "TEST");
        verify(kafkaProducerService, never()).sendMessage(any(), any());
    }

    @Test
    void publishPortfolioDelete_skipsBlankOwner() {
        publisher.publishPortfolioDelete(" ", UUID.randomUUID().toString(), "n", "TEST");
        verify(kafkaProducerService, never()).sendMessage(any(), any());
    }

    @Test
    void publishPortfolioDelete_skipsBlankPortfolioId() {
        publisher.publishPortfolioDelete("owner-1", null, "n", "TEST");
        verify(kafkaProducerService, never()).sendMessage(any(), any());
    }

    @Test
    void publishPortfolioDelete_nullName_stillPublishesWithUuid() {
        String owner = "owner-" + UUID.randomUUID();
        String portfolioId = UUID.randomUUID().toString();

        publisher.publishPortfolioDelete(owner, portfolioId, null, "TEST_SOURCE");

        ArgumentCaptor<PortfolioUpdateEvent> cap = ArgumentCaptor.forClass(PortfolioUpdateEvent.class);
        verify(kafkaProducerService).sendMessage(cap.capture(), isNull());
        assertEquals("DELETE", cap.getValue().getAction());
        assertEquals(portfolioId, cap.getValue().getPortfolioId());
        assertEquals(null, cap.getValue().getName());
    }
}
