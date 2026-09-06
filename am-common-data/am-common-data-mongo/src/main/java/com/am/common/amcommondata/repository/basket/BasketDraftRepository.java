package com.am.common.amcommondata.repository.basket;

import com.am.common.amcommondata.document.basket.BasketDraftDocument;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface BasketDraftRepository extends MongoRepository<BasketDraftDocument, String> {

    List<BasketDraftDocument> findByUserIdOrderByUpdatedAtDesc(String userId);

    Optional<BasketDraftDocument> findByIdAndUserId(String id, String userId);

    long deleteByIdAndUserId(String id, String userId);

    long countByUserId(String userId);

    Optional<BasketDraftDocument> findByUserIdAndSourcePortfolioIdAndEtfIsin(
            String userId, String sourcePortfolioId, String etfIsin);

    long deleteByUserIdAndSourcePortfolioIdAndEtfIsin(
            String userId, String sourcePortfolioId, String etfIsin);
}
