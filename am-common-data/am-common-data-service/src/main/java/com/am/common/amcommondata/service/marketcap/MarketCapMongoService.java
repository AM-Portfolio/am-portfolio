package com.am.common.amcommondata.service.marketcap;

import com.am.common.amcommondata.document.marketcap.MarketCapDocument;
import com.am.common.amcommondata.repository.marketcap.MarketCapMongoRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.BulkOperations;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;

import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class MarketCapMongoService {

    private final MarketCapMongoRepository repository;
    private final MongoTemplate mongoTemplate;

    public Map<String, MarketCapDocument> getBySymbols(List<String> symbols) {
        if (symbols == null || symbols.isEmpty()) {
            return Collections.emptyMap();
        }
        try {
            List<MarketCapDocument> docs = repository.findBySymbolIn(symbols);
            return docs.stream().collect(Collectors.toMap(MarketCapDocument::getSymbol, d -> d));
        } catch (Exception e) {
            log.error("Error retrieving market cap from MongoDB for symbols: {}", symbols, e);
            return Collections.emptyMap();
        }
    }

    public void saveAll(Collection<MarketCapDocument> documents) {
        if (documents == null || documents.isEmpty()) {
            return;
        }
        log.debug("Saving {} market cap documents to MongoDB using bulk upsert", documents.size());
        try {
            BulkOperations bulkOps = mongoTemplate.bulkOps(BulkOperations.BulkMode.UNORDERED, MarketCapDocument.class);
            for (MarketCapDocument doc : documents) {
                Query query = new Query(Criteria.where("_id").is(doc.getSymbol()));
                Update update = new Update()
                    .set("sector", doc.getSector())
                    .set("industry", doc.getIndustry())
                    .set("marketCapType", doc.getMarketCapType())
                    .set("marketCapValue", doc.getMarketCapValue())
                    .set("companyName", doc.getCompanyName())
                    .set("updatedAt", doc.getUpdatedAt());
                bulkOps.upsert(query, update);
            }
            bulkOps.execute();
        } catch (Exception e) {
            log.error("Error saving market cap to MongoDB", e);
        }
    }
}
