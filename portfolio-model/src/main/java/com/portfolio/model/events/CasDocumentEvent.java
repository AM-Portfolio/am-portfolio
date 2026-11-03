package com.portfolio.model.events;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import com.am.common.amcommondata.model.cas.CasDocumentMeta;
import com.am.common.amcommondata.model.cas.CasMfFolioGroup;
import com.am.common.amcommondata.model.cas.CasUnmappedDemat;
import com.am.common.amcommondata.model.demat.DematAccountModel;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
public class CasDocumentEvent {
    private UUID id;
    private String eventType;
    private String source;
    private String documentSource;
    private String userId;
    private LocalDate asOfDate;
    private LocalDate statementFrom;
    private LocalDate statementTo;
    private LocalDateTime timestamp;
    private CasDocumentMeta.Cover cover;
    private CasDocumentMeta.Validation validation;
    private List<DematAccountModel> accountDirectory;
    private List<CasMfFolioGroup> mfFolios;
    private List<CasUnmappedDemat> unmappedDematHoldings;
}
