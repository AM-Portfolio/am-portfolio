package com.portfolio.model.portfolio;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
@JsonInclude(Include.NON_NULL)
public class PortfolioHoldings {
    private List<EquityHoldings> equityHoldings;
}
