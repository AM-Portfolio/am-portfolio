package com.am.common.amcommondata.model.indices;

import com.am.common.amcommondata.model.asset.equity.EquityModel;
import com.am.common.amcommondata.model.common.AuditModel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EquityIndexMembershipModel {
    private UUID id;
    private EquityModel equity;
    private MarketIndexModel index;
    private LocalDate entryDate;
    private LocalDate exitDate;
    private Double weight;
    private AuditModel auditInfo;
}
