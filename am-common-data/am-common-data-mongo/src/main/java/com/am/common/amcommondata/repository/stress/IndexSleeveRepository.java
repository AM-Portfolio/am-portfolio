package com.am.common.amcommondata.repository.stress;

import com.am.common.amcommondata.document.stress.IndexSleeveDocument;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface IndexSleeveRepository extends MongoRepository<IndexSleeveDocument, String> {
    List<IndexSleeveDocument> findByActiveTrue();
    List<IndexSleeveDocument> findBySleeveTypeAndActiveTrue(String sleeveType);
    java.util.Optional<IndexSleeveDocument> findBySymbol(String symbol);
}
