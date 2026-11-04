package com.am.common.amcommondata.repository.cas;

import java.util.List;
import java.util.Optional;

import org.springframework.data.mongodb.repository.MongoRepository;

import com.am.common.amcommondata.document.cas.CasImportDocument;

public interface CasImportRepository extends MongoRepository<CasImportDocument, String> {
    Optional<CasImportDocument> findFirstByOwnerOrderByAsOfDateDescImportedAtDesc(String owner);

    List<CasImportDocument> findByOwnerOrderByAsOfDateDescImportedAtDesc(String owner);

    Optional<CasImportDocument> findFirstByOwnerAndFamilyMemberIdOrderByAsOfDateDescImportedAtDesc(
            String owner, String familyMemberId);

    Optional<CasImportDocument> findFirstByOwnerAndFamilyMemberIdIsNullOrderByAsOfDateDescImportedAtDesc(
            String owner);

    long deleteByOwnerAndFamilyMemberId(String owner, String familyMemberId);
}
