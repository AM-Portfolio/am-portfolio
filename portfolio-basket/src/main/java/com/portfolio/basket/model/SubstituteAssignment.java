package com.portfolio.basket.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class SubstituteAssignment {
    private String missingIsin;
    private String substituteIsin;
    private Double assignedWeight; // Null means consume up to max gap
}
