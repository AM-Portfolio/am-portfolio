package com.portfolio.model.analytics.intelligence;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Context-aware typeahead for Overview intelligence widgets.
 * {@code context}: STRESS_SECTOR | WHAT_IF_SYMBOL | WHAT_IF_SECTOR | CLASS_ADD_NAME
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class IntelligenceSuggestResponse {
    private String portfolioId;
    private String context;
    private String query;
    private List<SuggestionDto> suggestions;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class SuggestionDto {
        /** Value to put in the input / send to stress|what-if. */
        private String label;
        /** Optional secondary line (company name, alias note). */
        private String subtitle;
        /** HOLDING | PORTFOLIO_SECTOR | CANONICAL | MARKET | CLASS_TEMPLATE */
        private String source;
        /** Portfolio weight % when known (sectors). */
        private Double weightPct;
        /** Holdings matched if this sector were shocked (stress). */
        private Integer matchedHoldings;
        /** Symbol for WHAT_IF_SYMBOL. */
        private String symbol;
    }
}
