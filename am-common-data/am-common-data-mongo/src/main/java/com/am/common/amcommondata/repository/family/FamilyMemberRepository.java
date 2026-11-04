package com.am.common.amcommondata.repository.family;

import java.util.List;
import java.util.Optional;

import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;

import com.am.common.amcommondata.document.family.FamilyMemberDocument;

public interface FamilyMemberRepository
        extends MongoRepository<FamilyMemberDocument, String>, FamilyMemberRepositoryCustom {
    /**
     * BaseDocument maps {@code id} with {@code @Field("_id")}; derived {@code findById}
     * queries the wrong key {@code id}. Force Mongo {@code _id} (String UUID ids).
     */
    @Query("{ '_id': ?0 }")
    Optional<FamilyMemberDocument> findById(String id);

    List<FamilyMemberDocument> findByOwnerUserIdOrderByDisplayNameAsc(String ownerUserId);

    long countByOwnerUserId(String ownerUserId);

    Optional<FamilyMemberDocument> findFirstByOwnerUserIdAndEmailNorm(String ownerUserId, String emailNorm);

    Optional<FamilyMemberDocument> findFirstByOwnerUserIdAndNameNorm(String ownerUserId, String nameNorm);
}











