package com.am.common.amcommondata.model.asset.realestate;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class RentalInfo {
    private Double monthlyRent;
    private Double securityDeposit;
    private String leaseStartDate;
    private String leaseEndDate;
    private String tenantName;
    private String tenantContact;
    private String leaseTerms;
    private String paymentSchedule;
    private String maintenanceTerms;
    private Boolean isPetAllowed;
    private Double petDeposit;
    private String utilityResponsibilities;
    private String occupancyStatus;
    private Double occupancyRate;
}
