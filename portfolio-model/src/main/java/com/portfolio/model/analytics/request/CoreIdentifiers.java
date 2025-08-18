package com.portfolio.model.analytics.request;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

@Data
@AllArgsConstructor
@NoArgsConstructor
@SuperBuilder
public class CoreIdentifiers {
        /**
         * The portfolio ID to analyze
         */
        @NotBlank(message = "Portfolio ID is required")
        private String portfolioId;
        
        /**
         * The index symbol to analyze (e.g., "NIFTY 50", "NIFTY BANK")
         */
        @Pattern(regexp = "^[A-Za-z0-9 ]+$", message = "Index symbol must contain only alphanumeric characters and spaces")
        private String indexSymbol;
        
        /**
         * Optional comparison index symbol for relative performance analysis
         */
        @Pattern(regexp = "^[A-Za-z0-9 ]*$", message = "Comparison index symbol must contain only alphanumeric characters and spaces")
        private String comparisonIndexSymbol;
    }
    