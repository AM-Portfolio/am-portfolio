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
import java.util.List;

@Data
@SuperBuilder
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
@Document(collection = "portfolio_snapshots")
@CompoundIndex(name = "user_date_idx", def = "{'userId': 1, 'snapshotDate': 1}", unique = true)
public class PortfolioSnapshotDocument extends BaseDocument {

    @Field("snapshotId")
    private String snapshotId;

    @Field("userId")
    private String userId;

    @Field("snapshotDate")
    private LocalDate snapshotDate;

    @Field("totalUserWealth")
    private Double totalUserWealth;

    @Field("totalUserInvestment")
    private Double totalUserInvestment;

    @Field("totalUserGainLoss")
    private Double totalUserGainLoss;

    @Field("totalUserGainLossPercentage")
    private Double totalUserGainLossPercentage;

    @Field("portfolios")
    private List<PortfolioSnapshotEntry> portfolios;

    @Field("createdAt")
    private LocalDateTime createdAt;
}
