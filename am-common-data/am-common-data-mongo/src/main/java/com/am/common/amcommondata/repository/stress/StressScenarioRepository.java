package com.am.common.amcommondata.repository.stress;

import com.am.common.amcommondata.document.stress.StressScenarioDocument;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface StressScenarioRepository extends MongoRepository<StressScenarioDocument, String> {
    List<StressScenarioDocument> findByActiveTrueOrderBySortOrderAsc();
    Optional<StressScenarioDocument> findByScenarioCodeAndActiveTrue(String scenarioCode);
    Optional<StressScenarioDocument> findByScenarioCode(String scenarioCode);
}
