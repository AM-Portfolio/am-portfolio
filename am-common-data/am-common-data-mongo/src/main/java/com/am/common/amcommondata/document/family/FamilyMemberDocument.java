package com.am.common.amcommondata.document.family;

import java.time.LocalDateTime;

import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;

import com.am.common.amcommondata.document.base.BaseDocument;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

@Data
@SuperBuilder
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
@Document(collection = "family_members")
@org.springframework.data.mongodb.core.index.CompoundIndex(
        name = "family_owner_email_idx",
        def = "{'ownerUserId': 1, 'emailNorm': 1}")
@org.springframework.data.mongodb.core.index.CompoundIndex(
        name = "family_owner_name_idx",
        def = "{'ownerUserId': 1, 'nameNorm': 1}")
public class FamilyMemberDocument extends BaseDocument {
    @Field("ownerUserId")
    private String ownerUserId;

    @Field("displayName")
    private String displayName;

    @Field("email")
    private String email;

    @Field("emailNorm")
    private String emailNorm;

    @Field("nameNorm")
    private String nameNorm;

    @Field("source")
    private String source;

    @Field("createdAt")
    private LocalDateTime createdAt;
}
