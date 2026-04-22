package com.am.common.amcommondata.model.security;

import com.am.common.amcommondata.model.BaseModel;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

@Data
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class SecurityModel extends BaseModel {
    private SecurityKeyModel key;
    private SecurityMetadataModel metadata;
    private CompanyInfoModel companyInfo;
}
