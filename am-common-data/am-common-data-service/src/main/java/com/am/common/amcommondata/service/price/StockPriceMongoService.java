package com.am.common.amcommondata.service.price;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.BulkOperations;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Service;

import com.am.common.amcommondata.document.price.StockPriceDocument;
import com.am.common.amcommondata.repository.price.StockPriceMongoRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@RequiredArgsConstructor
@Slf4j
public class StockPriceMongoService {

    private final StockPriceMongoRepository stockPriceMongoRepository;
    private final MongoTemplate mongoTemplate;

    public void saveAll(List<StockPriceDocument> prices) {
        if (prices == null || prices.isEmpty()) {
            return;
        }
        log.debug("Saving {} stock prices to MongoDB using bulk upsert", prices.size());
        try {
            BulkOperations bulkOps = mongoTemplate.bulkOps(BulkOperations.BulkMode.UNORDERED, StockPriceDocument.class);
            for (StockPriceDocument price : prices) {
                Query query = new Query(Criteria.where("_id").is(price.getSymbol()));
                Update update = new Update()
                    .set("lastPrice", price.getLastPrice())
                    .set("previousClose", price.getPreviousClose())
                    .set("openPrice", price.getOpenPrice())
                    .set("highPrice", price.getHighPrice())
                    .set("lowPrice", price.getLowPrice())
                    .set("timestamp", price.getTimestamp())
                    .set("updatedAt", price.getUpdatedAt());
                bulkOps.upsert(query, update);
            }
            bulkOps.execute();
        } catch (Exception e) {
            log.error("Failed to save stock prices to MongoDB", e);
        }
    }

    public Map<String, StockPriceDocument> getPrices(List<String> symbols) {
        if (symbols == null || symbols.isEmpty()) {
            return Map.of();
        }
        return stockPriceMongoRepository.findBySymbolIn(symbols)
                .stream()
                .collect(Collectors.toMap(StockPriceDocument::getSymbol, doc -> doc));
    }

    public List<StockPriceDocument> findAll() {
        return stockPriceMongoRepository.findAll();
    }
}
