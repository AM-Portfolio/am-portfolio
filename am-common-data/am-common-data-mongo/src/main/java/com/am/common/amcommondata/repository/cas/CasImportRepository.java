package com.am.common.amcommondata.repository.cas;

import java.util.Optional;

import org.springframework.data.mongodb.repository.MongoRepository;

import com.am.common.amcommondata.document.cas.CasImportDocument;

public interface CasImportRepository extends MongoRepository<CasImportDocument, String> {
    Optional<CasImportDocument> findFirstByOwnerOrderByAsOfDateDescImportedAtDesc(String owner);
}
