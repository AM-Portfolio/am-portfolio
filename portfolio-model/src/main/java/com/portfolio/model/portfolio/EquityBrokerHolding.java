package com.portfolio.model.portfolio;

import com.am.common.amcommondata.model.enums.BrokerType;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@JsonInclude(Include.NON_NULL)
public class EquityBrokerHolding {
    private BrokerType brokerType;
    private Double quantity;

    public EquityBrokerHolding() {
    }

    public EquityBrokerHolding(BrokerType brokerType, Double quantity) {
        this.brokerType = brokerType;
        this.quantity = quantity;
    }
}
