package com.portfolio.api;

import com.am.common.amcommondata.model.enums.BrokerType;
import com.portfolio.model.events.PortfolioUpdateEvent;
import com.portfolio.model.mapper.PortfolioMapperv1;
import com.am.common.amcommondata.model.PortfolioModelV1;
import com.am.common.amcommondata.service.PortfolioService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/test")
public class FakeKafkaProducerController {

    @Autowired
    private PortfolioService portfolioService;

    @Autowired
    private PortfolioMapperv1 portfolioMapper;

    @Autowired
    private org.springframework.kafka.core.KafkaTemplate<String, Object> kafkaTemplate;

    @Value("${app.kafka.portfolio.stream.topic:am-portfolio}")
    private String inboundTopic;

    // Use this producer to send plain strings without double-JSON-serialization
    private org.springframework.kafka.core.KafkaTemplate<String, String> stringKafkaTemplate;

    @Autowired
    public void setStringKafkaTemplate(org.springframework.kafka.core.ProducerFactory<String, Object> producerFactory) {
        // Create a plain string template by using a separate producer config
        java.util.Map<String, Object> props = new java.util.HashMap<>(producerFactory.getConfigurationProperties());
        props.put(org.apache.kafka.clients.producer.ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, org.apache.kafka.common.serialization.StringSerializer.class);
        org.springframework.kafka.core.DefaultKafkaProducerFactory<String, String> stringFactory = new org.springframework.kafka.core.DefaultKafkaProducerFactory<>(props);
        this.stringKafkaTemplate = new org.springframework.kafka.core.KafkaTemplate<>(stringFactory);
    }

    @PostMapping("/produce-portfolio")
    public String producePortfolio(@RequestParam(defaultValue = "ZERODHA") BrokerType brokerType, 
                                   @RequestParam(defaultValue = "DOCUMENT") String source,
                                   @RequestParam(required = false) String portfolioId) {
        
        List<com.am.common.amcommondata.model.asset.equity.EquityModel> equities = generateMockEquities(brokerType);

        double totalInvestment = 0.0;
        double totalValue = 0.0;
        double totalGainLoss = 0.0;
        double todayGainLoss = 0.0;

        for (com.am.common.amcommondata.model.asset.equity.EquityModel eq : equities) {
            totalInvestment += eq.getInvestmentValue() != null ? eq.getInvestmentValue() : 0.0;
            totalValue += eq.getCurrentValue() != null ? eq.getCurrentValue() : 0.0;
            totalGainLoss += eq.getProfitLoss() != null ? eq.getProfitLoss() : 0.0;
            todayGainLoss += eq.getTodayProfitLoss() != null ? eq.getTodayProfitLoss() : 0.0;
        }

        double totalGainLossPercentage = totalInvestment > 0 ? (totalGainLoss / totalInvestment) * 100.0 : 0.0;
        double todayGainLossPercentage = totalValue > 0 ? (todayGainLoss / (totalValue - todayGainLoss)) * 100.0 : 0.0;

        String finalPortfolioId = portfolioId != null && !portfolioId.isEmpty() ? portfolioId : "test-portfolio-id-" + System.currentTimeMillis();

        PortfolioUpdateEvent event = PortfolioUpdateEvent.builder()
                .id(UUID.randomUUID())
                .brokerType(brokerType)
                .source(source)
                .userId("test-user-id")
                .portfolioId(finalPortfolioId)
                .equities(equities)
                .mutualFunds(new ArrayList<>())
                .totalValue(totalValue)
                .totalInvestment(totalInvestment)
                .totalGainLoss(totalGainLoss)
                .totalGainLossPercentage(totalGainLossPercentage)
                .todayGainLoss(todayGainLoss)
                .todayGainLossPercentage(todayGainLossPercentage)
                .timestamp(LocalDateTime.now())
                .build();

        // Send to Kafka so the consumer picks it up and fires our new code!
        // Using stringKafkaTemplate to avoid double-JSON serialization (JsonSerializer wrapping a String causes failure)
        try {
            com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
            mapper.registerModule(new com.fasterxml.jackson.datatype.jsr310.JavaTimeModule());
            String jsonPayload = mapper.writeValueAsString(event);
            stringKafkaTemplate.send(inboundTopic, jsonPayload);
        } catch (Exception e) {
            e.printStackTrace();
            return "Failed to send to Kafka: " + e.getMessage();
        }

        return "Successfully published mock event to Kafka topic " + inboundTopic + " for " + brokerType;
    }

    private List<com.am.common.amcommondata.model.asset.equity.EquityModel> generateMockEquities(BrokerType brokerType) {
        List<com.am.common.amcommondata.model.asset.equity.EquityModel> list = new ArrayList<>();
        
        switch (brokerType) {
            case ZERODHA:
                list.add(createMockEquity("HDFCBANK", "HDFC Bank Ltd.", 100.0, 1500.0, 1600.0, 10.0));
                list.add(createMockEquity("RELIANCE", "Reliance Industries", 50.0, 2500.0, 2600.0, 25.0));
                list.add(createMockEquity("TATAMOTORS", "Tata Motors Ltd.", 200.0, 800.0, 950.0, 15.0));
                list.add(createMockEquity("INFY", "Infosys Ltd.", 75.0, 1400.0, 1420.0, -5.0));
                list.add(createMockEquity("SBIN", "State Bank of India", 150.0, 700.0, 750.0, 8.0));
                break;
            case DHAN:
                list.add(createMockEquity("TCS", "Tata Consultancy Services", 50.0, 3800.0, 3950.0, 30.0));
                list.add(createMockEquity("ITC", "ITC Ltd.", 500.0, 400.0, 420.0, -2.0));
                list.add(createMockEquity("WIPRO", "Wipro Ltd.", 300.0, 450.0, 480.0, 5.0));
                list.add(createMockEquity("BHARTIARTL", "Bharti Airtel", 120.0, 1100.0, 1150.0, 12.0));
                list.add(createMockEquity("ASIANPAINT", "Asian Paints", 40.0, 2800.0, 2900.0, 20.0));
                break;
            case GROW:
                list.add(createMockEquity("ZOMATO", "Zomato Ltd.", 1000.0, 100.0, 160.0, 4.0));
                list.add(createMockEquity("ICICIBANK", "ICICI Bank Ltd.", 200.0, 1000.0, 1100.0, 15.0));
                list.add(createMockEquity("AXISBANK", "Axis Bank Ltd.", 150.0, 950.0, 1050.0, 10.0));
                list.add(createMockEquity("LT", "Larsen & Toubro", 80.0, 3200.0, 3400.0, 40.0));
                list.add(createMockEquity("BAJFINANCE", "Bajaj Finance", 30.0, 6800.0, 7000.0, 50.0));
                break;
            default:
                list.add(createMockEquity("DEFAULT1", "Default Stock 1", 10.0, 100.0, 110.0, 1.0));
                list.add(createMockEquity("DEFAULT2", "Default Stock 2", 20.0, 200.0, 190.0, -2.0));
                list.add(createMockEquity("DEFAULT3", "Default Stock 3", 30.0, 300.0, 320.0, 5.0));
                list.add(createMockEquity("DEFAULT4", "Default Stock 4", 40.0, 400.0, 440.0, 8.0));
                list.add(createMockEquity("DEFAULT5", "Default Stock 5", 50.0, 500.0, 550.0, 10.0));
                break;
        }
        return list;
    }

    private com.am.common.amcommondata.model.asset.equity.EquityModel createMockEquity(String symbol, String name, double qty, double avgPrice, double curPrice, double todayPL) {
        double investmentValue = qty * avgPrice;
        double currentValue = qty * curPrice;
        double profitLoss = currentValue - investmentValue;
        double profitLossPercentage = investmentValue > 0 ? (profitLoss / investmentValue) * 100.0 : 0.0;
        
        return com.am.common.amcommondata.model.asset.equity.EquityModel.builder()
                .id(UUID.randomUUID())
                .symbol(symbol)
                .name(name)
                .quantity(qty)
                .avgBuyingPrice(avgPrice)
                .currentPrice(curPrice)
                .investmentValue(investmentValue)
                .currentValue(currentValue)
                .profitLoss(profitLoss)
                .profitLossPercentage(profitLossPercentage)
                .todayProfitLoss(todayPL)
                .todayProfitLossPercentage((todayPL / (currentValue - todayPL)) * 100.0)
                .assetType(com.am.common.amcommondata.model.enums.AssetType.EQUITY)
                .brokerType(null) // typically inherited from parent portfolio anyway
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();
    }
}
