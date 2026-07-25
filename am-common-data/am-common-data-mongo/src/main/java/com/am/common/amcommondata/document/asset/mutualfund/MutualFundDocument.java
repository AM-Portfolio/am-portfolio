package com.am.common.amcommondata.document.asset.mutualfund;

import org.springframework.data.mongodb.core.mapping.Field;

import com.am.common.amcommondata.document.asset.AssetDocument;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;
import java.time.LocalDate;

@Data
@SuperBuilder
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class MutualFundDocument extends AssetDocument {
    private String isin;
    private String fundHouse;
    private String category;
    private String subCategory;
    private String schemeType;
    private Double aum;
    private Double nav;
    private Double expenseRatio;
    private Double exitLoad;
    private Double minInvestment;
    private String fundManager;
    private LocalDate inceptionDate;
    private Double returnOneYear;
    private Double returnThreeYear;
    private Double returnFiveYear;
    private String riskLevel;
    private String investmentStrategy;
    private String benchmarkIndex;
    private Boolean directPlan;
    private String sipFrequency;
    private Double sipMinimumAmount;
}
