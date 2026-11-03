package com.am.common.amcommondata.document.nps;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;

import com.am.common.amcommondata.document.base.BaseDocument;
import com.am.common.amcommondata.model.nps.NpsDocumentMeta;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

/** Latest NPS statement envelope per user (PRAN, tiers, validation). */
@Data
@SuperBuilder
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
@Document(collection = "nps_imports")
@org.springframework.data.mongodb.core.index.CompoundIndex(
        name = "nps_owner_asof_idx",
        def = "{'owner': 1, 'asOfDate': -1}")
public class NpsImportDocument extends BaseDocument {
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

    @Field("pran")
    private String pran;

    @Field("subscriber")
    private Map<String, Object> subscriber;

    @Field("tiers")
    private List<Map<String, Object>> tiers;

    @Field("validation")
    private NpsDocumentMeta.Validation validation;
}
