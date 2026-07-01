package com.am.common.amcommondata.repository.portfolio;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import org.springframework.data.mongodb.repository.Aggregation;
import org.springframework.data.mongodb.repository.Query;
import org.springframework.stereotype.Repository;

import com.am.common.amcommondata.document.portfolio.PortfolioDocument;
import com.am.common.amcommondata.repository.base.BaseRepository;

@Repository
public interface PortfolioDocumentRepository extends BaseRepository<PortfolioDocument> {
    List<PortfolioDocument> findByOwner(String owner);
    Optional<PortfolioDocument> findById(String id);
    List<PortfolioDocument> findByOwnerAndBrokerType(String owner, com.am.common.amcommondata.model.enums.BrokerType brokerType);

    @Aggregation(pipeline = { "{ '$group': { '_id': '$owner' } }" })
    List<String> findAllDistinctOwners();

    /**
     * Returns distinct userIds (owners) who have logged in on or after the given cutoffDate.
     * Used by the daily snapshot scheduler to skip inactive users.
     */
    @Aggregation(pipeline = {
        "{ '$match': { 'lastLoginDate': { '$gte': ?0 } } }",
        "{ '$group': { '_id': '$owner' } }"
    })
    List<String> findActiveOwnersSince(LocalDate cutoffDate);
}
