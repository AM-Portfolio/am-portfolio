package com.am.common.amcommondata.repository.marketcap;

import com.am.common.amcommondata.document.marketcap.MarketCapDocument;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface MarketCapMongoRepository extends MongoRepository<MarketCapDocument, String> {
    List<MarketCapDocument> findBySymbolIn(List<String> symbols);
}
