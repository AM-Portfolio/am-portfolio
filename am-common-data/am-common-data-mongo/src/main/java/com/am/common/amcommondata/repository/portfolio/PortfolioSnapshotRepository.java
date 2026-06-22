package com.am.common.amcommondata.repository.portfolio;

import com.am.common.amcommondata.document.portfolio.PortfolioSnapshotDocument;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
public interface PortfolioSnapshotRepository extends MongoRepository<PortfolioSnapshotDocument, String> {

    // Fetch the latest N snapshots for a portfolio (ordered by date descending)
    List<PortfolioSnapshotDocument> findByPortfolioIdOrderBySnapshotDateDesc(String portfolioId, Pageable pageable);

    // Fetch snapshot for a specific day to prevent duplicates (idempotency)
    Optional<PortfolioSnapshotDocument> findByPortfolioIdAndSnapshotDate(String portfolioId, LocalDate snapshotDate);
}
