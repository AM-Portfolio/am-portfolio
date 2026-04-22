package com.am.common.amcommondata.repository.portfolio;

import java.util.List;
import java.util.Optional;

import org.springframework.data.mongodb.repository.Aggregation;
import org.springframework.stereotype.Repository;

import com.am.common.amcommondata.document.portfolio.PortfolioDocument;
import com.am.common.amcommondata.repository.base.BaseRepository;

@Repository
public interface PortfolioDocumentRepository extends BaseRepository<PortfolioDocument> {
    List<PortfolioDocument> findByOwner(String owner);
    Optional<PortfolioDocument> findById(String id);

    @Aggregation(pipeline = { "{ '$group': { '_id': '$owner' } }" })
    List<String> findAllDistinctOwners();
}
