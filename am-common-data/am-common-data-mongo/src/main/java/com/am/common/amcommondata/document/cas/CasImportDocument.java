package com.am.common.amcommondata.document.cas;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;

import com.am.common.amcommondata.document.base.BaseDocument;
import com.am.common.amcommondata.model.cas.CasDocumentMeta;
import com.am.common.amcommondata.model.cas.CasMfFolioGroup;
import com.am.common.amcommondata.model.cas.CasUnmappedDemat;
import com.am.common.amcommondata.model.demat.DematAccountModel;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

/** Latest CDSL CAS envelope per user (directory, validation, MF folio groups). */
@Data
@SuperBuilder
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
@Document(collection = "cas_imports")
@org.springframework.data.mongodb.core.index.CompoundIndex(
        name = "cas_owner_asof_idx",
        def = "{'owner': 1, 'asOfDate': -1}")
public class CasImportDocument extends BaseDocument {
    @Field("owner")
    private String owner;

    @Field("documentSource")
    private String documentSource;

    @Field("asOfDate")
    private LocalDate asOfDate;

    @Field("statementFrom")
    private LocalDate statementFrom;

    @Field("statementTo")
    private LocalDate statementTo;

    @Field("importedAt")
    private LocalDateTime importedAt;

    @Field("cover")
    private CasDocumentMeta.Cover cover;

    @Field("validation")
    private CasDocumentMeta.Validation validation;

    @Field("accountDirectory")
    private List<DematAccountModel> accountDirectory;

    @Field("mfFolios")
    private List<CasMfFolioGroup> mfFolios;

    @Field("unmappedDematHoldings")
    private List<CasUnmappedDemat> unmappedDematHoldings;

    @Field("familyMemberId")
    private String familyMemberId;

    @Field("familyMemberName")
    private String familyMemberName;

    @Field("identityMatch")
    private String identityMatch;

    @Field("holderName")
    private String holderName;

    @Field("holderEmail")
    private String holderEmail;
}
