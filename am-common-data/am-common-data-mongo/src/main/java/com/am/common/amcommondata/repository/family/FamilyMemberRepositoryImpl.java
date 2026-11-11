package com.am.common.amcommondata.repository.family;

import java.util.Optional;

import org.bson.Document;
import org.bson.types.ObjectId;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;

import com.am.common.amcommondata.document.family.FamilyMemberDocument;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.model.Filters;

import lombok.RequiredArgsConstructor;

/**
 * Spring Data custom fragment (name must be {@code FamilyMemberRepositoryImpl}).
 * Domain property {@code id} maps to Mongo {@code _id}; query with property names so
 * the converter can match both String UUIDs and BSON ObjectIds.
 */
@RequiredArgsConstructor
public class FamilyMemberRepositoryImpl implements FamilyMemberRepositoryCustom {

    private final MongoTemplate mongoTemplate;

    @Override
    public Optional<FamilyMemberDocument> findOwnedById(String id, String ownerUserId) {
        if (id == null || id.isBlank() || ownerUserId == null || ownerUserId.isBlank()) {
            return Optional.empty();
        }
        Criteria idMatch = Criteria.where("id").is(id);
        if (ObjectId.isValid(id)) {
            idMatch = new Criteria().orOperator(
                    Criteria.where("id").is(id),
                    Criteria.where("id").is(new ObjectId(id)));
        }
        Query query = Query.query(new Criteria().andOperator(
                idMatch,
                Criteria.where("ownerUserId").is(ownerUserId)));
        // Prefer String _id twin when both exist (richer CAS-updated row).
        if (ObjectId.isValid(id)) {
            FamilyMemberDocument asString = mongoTemplate.findOne(
                    Query.query(new Criteria().andOperator(
                            Criteria.where("id").is(id),
                            Criteria.where("ownerUserId").is(ownerUserId))),
                    FamilyMemberDocument.class);
            if (asString != null) {
                return Optional.of(asString);
            }
        }
        return Optional.ofNullable(mongoTemplate.findOne(query, FamilyMemberDocument.class));
    }

    @Override
    public FamilyMemberDocument savePreservingBsonId(FamilyMemberDocument doc) {
        if (doc == null) {
            return null;
        }
        String id = doc.getId();
        String owner = doc.getOwnerUserId();
        if (id == null || id.isBlank()) {
            return mongoTemplate.save(doc);
        }
        if (ObjectId.isValid(id) && owner != null && !owner.isBlank()) {
            MongoCollection<Document> col = mongoTemplate.getCollection(
                    mongoTemplate.getCollectionName(FamilyMemberDocument.class));
            Document stringRaw = col.find(Filters.and(
                    Filters.eq("_id", id),
                    Filters.eq("ownerUserId", owner))).first();
            Document oidRaw = col.find(Filters.and(
                    Filters.eq("_id", new ObjectId(id)),
                    Filters.eq("ownerUserId", owner))).first();

            if (stringRaw != null) {
                applyUpdate(Criteria.where("id").is(id).and("ownerUserId").is(owner), doc);
                if (oidRaw != null) {
                    col.deleteOne(Filters.and(
                            Filters.eq("_id", new ObjectId(id)),
                            Filters.eq("ownerUserId", owner)));
                }
                return mongoTemplate.findOne(
                        Query.query(Criteria.where("id").is(id).and("ownerUserId").is(owner)),
                        FamilyMemberDocument.class);
            }
            if (oidRaw != null) {
                // Promote ObjectId → String once so later Spring saves never twin.
                Document promoted = new Document(oidRaw);
                promoted.put("_id", id);
                mergeDocFields(promoted, doc);
                col.insertOne(promoted);
                col.deleteOne(Filters.and(
                        Filters.eq("_id", new ObjectId(id)),
                        Filters.eq("ownerUserId", owner)));
                return mongoTemplate.findOne(
                        Query.query(Criteria.where("id").is(id).and("ownerUserId").is(owner)),
                        FamilyMemberDocument.class);
            }
        }
        return mongoTemplate.save(doc);
    }

    @Override
    public int consolidateObjectIdStringTwins(String ownerUserId) {
        if (ownerUserId == null || ownerUserId.isBlank()) {
            return 0;
        }
        MongoCollection<Document> col = mongoTemplate.getCollection(
                mongoTemplate.getCollectionName(FamilyMemberDocument.class));
        int removed = 0;
        for (Document d : col.find(Filters.eq("ownerUserId", ownerUserId))) {
            Object rawId = d.get("_id");
            if (!(rawId instanceof ObjectId oid)) {
                continue;
            }
            String hex = oid.toHexString();
            Document stringTwin = col.find(Filters.and(
                    Filters.eq("_id", hex),
                    Filters.eq("ownerUserId", ownerUserId))).first();
            if (stringTwin == null) {
                // Promote lone ObjectId → String so future saves stay typed.
                Document promoted = new Document(d);
                promoted.put("_id", hex);
                col.insertOne(promoted);
            }
            col.deleteOne(Filters.and(
                    Filters.eq("_id", oid),
                    Filters.eq("ownerUserId", ownerUserId)));
            removed++;
        }
        return removed;
    }

    private void applyUpdate(Criteria match, FamilyMemberDocument doc) {
        Update update = new Update()
                .set("displayName", doc.getDisplayName())
                .set("email", doc.getEmail())
                .set("emailNorm", doc.getEmailNorm())
                .set("nameNorm", doc.getNameNorm())
                .set("source", doc.getSource())
                .set("createdAt", doc.getCreatedAt());
        mongoTemplate.updateFirst(Query.query(match), update, FamilyMemberDocument.class);
    }

    private static void mergeDocFields(Document target, FamilyMemberDocument doc) {
        if (doc.getDisplayName() != null) {
            target.put("displayName", doc.getDisplayName());
        }
        if (doc.getEmail() != null) {
            target.put("email", doc.getEmail());
        }
        if (doc.getEmailNorm() != null) {
            target.put("emailNorm", doc.getEmailNorm());
        }
        if (doc.getNameNorm() != null) {
            target.put("nameNorm", doc.getNameNorm());
        }
        if (doc.getSource() != null) {
            target.put("source", doc.getSource());
        }
        if (doc.getCreatedAt() != null) {
            target.put("createdAt", doc.getCreatedAt());
        }
    }
}
