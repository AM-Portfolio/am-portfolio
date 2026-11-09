package com.portfolio.basket.model;

import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
public class SubstituteAssignment {
    private String missingIsin;
    /** Preferred: holding ISIN. May also be a ticker when ISIN is unknown. */
    private String substituteIsin;
    private Double assignedWeight; // Null means consume up to max gap
    /** Optional ticker fallback when substituteIsin is blank or not found as ISIN. */
    private String substituteSymbol;

    public SubstituteAssignment(String missingIsin, String substituteIsin, Double assignedWeight) {
        this(missingIsin, substituteIsin, assignedWeight, null);
    }

    public SubstituteAssignment(String missingIsin, String substituteIsin, Double assignedWeight,
            String substituteSymbol) {
        this.missingIsin = missingIsin;
        this.substituteIsin = substituteIsin;
        this.assignedWeight = assignedWeight;
        this.substituteSymbol = substituteSymbol;
    }
}
