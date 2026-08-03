package com.am.common.amcommondata.repository.portfolio;

import com.am.common.amcommondata.document.portfolio.PortfolioIntradaySessionDocument;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.Optional;

@Repository
public interface PortfolioIntradaySessionRepository extends MongoRepository<PortfolioIntradaySessionDocument, String> {
    Optional<PortfolioIntradaySessionDocument> findByUserIdAndPortfolioIdAndSessionDate(
            String userId, String portfolioId, LocalDate sessionDate);

    Optional<PortfolioIntradaySessionDocument> findFirstByUserIdAndPortfolioIdOrderBySessionDateDesc(
            String userId, String portfolioId);
}
