package com.am.common.amcommondata.repository.portfolio;

import com.am.common.amcommondata.document.portfolio.PortfolioSnapshotDocument;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.Set;

@Repository
public interface PortfolioSnapshotRepository extends MongoRepository<PortfolioSnapshotDocument, String> {

    // Fetch the latest N snapshots for a user (ordered by date descending)
    List<PortfolioSnapshotDocument> findByUserIdOrderBySnapshotDateDesc(String userId, Pageable pageable);

    // Fetch snapshot for a specific day to prevent duplicates (idempotency)
    Optional<PortfolioSnapshotDocument> findByUserIdAndSnapshotDate(String userId, LocalDate snapshotDate);

    // Fetch all existing snapshot dates for a user within a date range (batch idempotency for catch-up)
    List<PortfolioSnapshotDocument> findByUserIdAndSnapshotDateBetween(String userId, LocalDate from, LocalDate to);
}

