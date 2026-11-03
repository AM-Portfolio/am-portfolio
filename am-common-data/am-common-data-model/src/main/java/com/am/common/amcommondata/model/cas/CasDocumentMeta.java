package com.am.common.amcommondata.model.cas;

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
public class CasDocumentMeta {
    private String documentSource;
    private LocalDate asOfDate;
    private LocalDate statementFrom;
    private LocalDate statementTo;
    private LocalDateTime importedAt;
    private Cover cover;
    private Validation validation;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonInclude(Include.NON_NULL)
    public static class Cover {
        private Integer totalAccounts;
        private Integer dematAccounts;
        private Integer mfFolios;
        private Double totalValue;
        private Double cdslDematAccounts;
        private Double nsdlDematAccounts;
        private Double mutualFundFolios;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonInclude(Include.NON_NULL)
    public static class Validation {
        private Boolean allOk;
        private Double extractedTotal;
        private Map<String, Double> extractedByAssetClass;
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
