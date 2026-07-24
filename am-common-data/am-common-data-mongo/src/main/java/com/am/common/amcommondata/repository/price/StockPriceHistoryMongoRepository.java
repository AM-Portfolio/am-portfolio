package com.am.common.amcommondata.repository.price;

import java.util.Collection;
import java.util.List;

import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import com.am.common.amcommondata.document.price.StockPriceHistoryDocument;

@Repository
public interface StockPriceHistoryMongoRepository extends MongoRepository<StockPriceHistoryDocument, String> {
    
    List<StockPriceHistoryDocument> findBySymbolInAndTimestampMinuteGreaterThanEqualOrderByTimestampMinuteAsc(
        Collection<String> symbols, Long startTimestampMinute
    );
}
