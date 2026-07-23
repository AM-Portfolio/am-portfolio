package com.portfolio.kafka.consumer;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;
import com.portfolio.marketdata.service.MarketDataService;
import java.lang.reflect.Method;

class StockIndicesUpdateConsumerServiceTest {

    @Test
    void kafkaSymbolNormalizationMatchesServiceCleanSymbol() throws Exception {
        // Given
        String inputSymbol = "NSE:RELIANCE-EQ.NS";
        
        // When Service cleans it
        Method method = MarketDataService.class.getDeclaredMethod("cleanSymbol", String.class);
        method.setAccessible(true);
        MarketDataService service = new MarketDataService(null, null, null, null, null);
        String serviceReads = (String) method.invoke(service, inputSymbol);
        
        // And when Consumer cleans it
        StockIndicesUpdateConsumerService consumer = new StockIndicesUpdateConsumerService(null, null);
        String kafkaSaved = consumer.cleanSymbol(inputSymbol);
        
        // Then
        assertEquals("RELIANCE-EQ", serviceReads, "Service should strip prefix and suffix");
        assertEquals(serviceReads, kafkaSaved, "Kafka consumer must normalize exactly the same way as MarketDataService");
    }
}
