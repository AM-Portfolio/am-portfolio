package com.am.common.amcommondata.model.nps;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(Include.NON_NULL)
public class NpsDocumentMeta {
    private String documentSource;
    private LocalDate asOfDate;
    private LocalDate statementFrom;
    private LocalDate statementTo;
    private LocalDateTime importedAt;
    private String pran;
    private String subscriberName;
    private String subscriberEmail;
    private String subscriberMobile;
    /** Formatted nominee, e.g. "ABHIMANYU KUMAR (100%)". */
    private String nomineeDetails;
    /** Statement registration date string as on NPS PDF (e.g. "21-Dec-20"). */
    private String registrationDate;
    private Double tier1Value;
    private Double tier2Value;
    private Double totalValue;
    private Validation validation;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonInclude(Include.NON_NULL)
    public static class Validation {
        private Boolean allOk;
        private Double extractedTotal;
        private Map<String, Double> extractedByTier;
        private List<Check> checks;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonInclude(Include.NON_NULL)
    public static class Check {
        private String name;
        private Double expected;
        private Double actual;
        private Double diff;
        private Boolean ok;
    }
}
