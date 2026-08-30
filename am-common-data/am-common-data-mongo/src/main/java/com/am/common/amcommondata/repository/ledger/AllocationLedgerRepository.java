package com.am.common.amcommondata.repository.ledger;

import com.am.common.amcommondata.document.ledger.AllocationLedgerEntry;
import com.am.common.amcommondata.model.ledger.AllocationLedgerStatus;
import org.springframework.data.mongodb.repository.Aggregation;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface AllocationLedgerRepository extends MongoRepository<AllocationLedgerEntry, String> {
    
    List<AllocationLedgerEntry> findByBasketIdAndStatus(String basketId, AllocationLedgerStatus status);
    
    List<AllocationLedgerEntry> findByBrokerPortfolioIdAndIsinAndStatus(String brokerPortfolioId, String isin, AllocationLedgerStatus status);
    
    @Aggregation(pipeline = {
        "{ '$match': { 'brokerPortfolioId': ?0, 'isin': ?1, 'status': 'ACTIVE' } }",
        "{ '$group': { '_id': null, 'totalQuantity': { '$sum': '$quantity' } } }"
    })
    Double sumActiveQuantityByBrokerPortfolioIdAndIsin(String brokerPortfolioId, String isin);

    @Aggregation(pipeline = {
        "{ '$match': { 'brokerPortfolioId': ?0, 'status': 'ACTIVE' } }",
        "{ '$group': { '_id': '$isin', 'totalQuantity': { '$sum': '$quantity' } } }"
    })
    List<com.am.common.amcommondata.document.ledger.AllocationLedgerSumResult> sumActiveQuantitiesByBrokerPortfolioId(String brokerPortfolioId);
}
