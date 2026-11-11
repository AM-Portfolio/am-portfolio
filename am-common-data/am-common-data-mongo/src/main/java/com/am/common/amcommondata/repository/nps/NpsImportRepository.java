package com.am.common.amcommondata.repository.nps;

import java.util.List;
import java.util.Optional;

import org.springframework.data.mongodb.repository.MongoRepository;

import com.am.common.amcommondata.document.nps.NpsImportDocument;

public interface NpsImportRepository extends MongoRepository<NpsImportDocument, String> {
    Optional<NpsImportDocument> findFirstByOwnerOrderByAsOfDateDescImportedAtDesc(String owner);

    List<NpsImportDocument> findByOwnerOrderByAsOfDateDescImportedAtDesc(String owner);

    Optional<NpsImportDocument> findFirstByOwnerAndFamilyMemberIdIsNullOrderByAsOfDateDescImportedAtDesc(
            String owner);

    Optional<NpsImportDocument> findFirstByOwnerAndFamilyMemberIdOrderByAsOfDateDescImportedAtDesc(
            String owner, String familyMemberId);

    long deleteByOwnerAndFamilyMemberId(String owner, String familyMemberId);
}
