package com.portfolio.model.events;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.am.common.amcommondata.model.nps.NpsDocumentMeta;
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
public class NpsDocumentEvent {
    private UUID id;
    private String eventType;
    private String source;
    private String documentSource;
    private String userId;
    private LocalDate asOfDate;
    private LocalDate statementFrom;
    private LocalDate statementTo;
    private LocalDateTime timestamp;
    private Map<String, Object> subscriber;
    private List<Map<String, Object>> tiers;
    private NpsDocumentMeta.Validation validation;
    /** SELF | FAMILY | UNKNOWN from email-extractor. */
    private String identityMatch;
    private String holderName;
    private String holderEmail;
}
