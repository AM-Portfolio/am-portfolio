package com.am.common.amcommondata.repository.family;

import java.util.Optional;

import com.am.common.amcommondata.document.family.FamilyMemberDocument;

public interface FamilyMemberRepositoryCustom {
    Optional<FamilyMemberDocument> findOwnedById(String id, String ownerUserId);

    /**
     * Persist without creating an ObjectId/String twin for the same hex {@code _id}.
     * Updates the existing BSON type in place when present.
     */
    FamilyMemberDocument savePreservingBsonId(FamilyMemberDocument doc);

    /**
     * For each hex id that exists as both ObjectId and String under the owner,
     * keep the richer String doc and remove the ObjectId twin.
     */
    int consolidateObjectIdStringTwins(String ownerUserId);
}
