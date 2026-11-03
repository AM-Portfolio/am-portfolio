package com.am.common.amcommondata.repository.nps;

import java.util.Optional;

import org.springframework.data.mongodb.repository.MongoRepository;

import com.am.common.amcommondata.document.nps.NpsImportDocument;

public interface NpsImportRepository extends MongoRepository<NpsImportDocument, String> {
    Optional<NpsImportDocument> findFirstByOwnerOrderByAsOfDateDescImportedAtDesc(String owner);
}
