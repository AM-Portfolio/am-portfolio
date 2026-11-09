package com.am.common.amcommondata.repository.price;

import java.util.Collection;
import java.util.List;

import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import com.am.common.amcommondata.document.price.StockPriceDocument;

@Repository
public interface StockPriceMongoRepository extends MongoRepository<StockPriceDocument, String> {
    List<StockPriceDocument> findBySymbolIn(Collection<String> symbols);
}
