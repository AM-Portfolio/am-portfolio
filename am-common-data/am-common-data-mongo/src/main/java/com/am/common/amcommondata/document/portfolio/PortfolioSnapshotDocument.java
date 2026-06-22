package com.am.common.amcommondata.document.portfolio;

import com.am.common.amcommondata.document.base.BaseDocument;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@SuperBuilder
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
@Document(collection = "portfolio_snapshots")
@CompoundIndex(name = "portfolio_date_idx", def = "{'portfolioId': 1, 'snapshotDate': 1}", unique = true)
public class PortfolioSnapshotDocument extends BaseDocument {

    @Field("portfolioId")
    private String portfolioId;

    @Field("userId")
    private String userId;

    @Field("snapshotDate")
    private LocalDate snapshotDate;

    @Field("open")
    private Double open;

    @Field("high")
    private Double high;

    @Field("low")
    private Double low;

    @Field("close")
    private Double close;

    @Field("totalInvestment")
    private Double totalInvestment;

    @Field("totalGainLoss")
    private Double totalGainLoss;

    @Field("totalGainLossPercentage")
    private Double totalGainLossPercentage;

    @Field("createdAt")
    private LocalDateTime createdAt;

}
