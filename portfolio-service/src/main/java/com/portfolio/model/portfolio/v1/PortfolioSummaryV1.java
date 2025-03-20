package com.portfolio.model.portfolio.v1;

import java.util.Map;

import com.am.common.amcommondata.model.enums.BrokerType;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.experimental.SuperBuilder;

@Data
@SuperBuilder
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
@JsonInclude(Include.NON_NULL)
public class PortfolioSummaryV1 extends BasePortfolioSummay {
    private Map<BrokerType, BrokerPortfolioSummary> brokerPortfolios;
}
