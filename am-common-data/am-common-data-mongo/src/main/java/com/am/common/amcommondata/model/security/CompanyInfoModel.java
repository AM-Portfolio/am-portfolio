package com.am.common.amcommondata.model.security;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CompanyInfoModel {
    private String legalName;
    private String symbol;
    private String isin;
    private String cin;
    private String pan;
    private String website;
    private String email;
    private String phoneNumber;
    private String description;
    private String businessDescription;
}
