package com.am.common.amcommondata.document.portfolio;

import com.am.common.amcommondata.document.base.BaseDocument;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
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
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
@Document(collection = "portfolio_intraday_sessions")
@CompoundIndex(name = "user_portfolio_date_idx", def = "{'userId': 1, 'portfolioId': 1, 'sessionDate': 1}", unique = true)
public class PortfolioIntradaySessionDocument extends BaseDocument {

    @Field("userId")
    private String userId;

    @Field("portfolioId")
    private String portfolioId;

    @Field("sessionDate")
    private LocalDate sessionDate;

    @Field("baselineWealth")
    private Double baselineWealth;

    @Field("dataPoints")
    private List<SessionDataPoint> dataPoints;

    @Field("createdAt")
    @org.springframework.data.mongodb.core.index.Indexed(expireAfterSeconds = 2592000)
    private LocalDateTime createdAt;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SessionDataPoint {
        private String timestamp;
        private Double totalWealth;
        private Double changeFromOpen;
        private Double changeFromOpenPct;
    }
}
