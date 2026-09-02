package com.am.common.amcommondata.repository.basket;

import com.am.common.amcommondata.document.basket.BasketCreateIdempotencyDocument;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface BasketCreateIdempotencyRepository
        extends MongoRepository<BasketCreateIdempotencyDocument, String> {
}
