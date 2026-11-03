package com.am.common.amcommondata.model.demat;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(Include.NON_NULL)
public class DematAccountModel {
    private String accountNo;
    private String accountType;
    private String status;
    private String email;
    private String mobile;
    /** Holder / investor name when present on CAS profile */
    private String holderName;
    private String dpId;
    private String clientId;
    private String boid;
    private String dpName;
    private String nominee;
    /** True when CAS shows a registered nominee (not "NOT REGISTERED"). */
    private Boolean nomineeRegistered;
    /** CDSL/NSDL frozen status, e.g. "Not Frozen" / "Frozen for Debit". */
    private String frozenStatus;
    private Double holdingValue;
    /** Broker label when known (FYERS/GROWW/…); free string so unknown DPs don't break CAS import. */
    private String mappedBroker;
    private String amcName;
    private String folio;
    private Boolean active;
    private String portfolioId;
}
