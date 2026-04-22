package com.am.common.amcommondata.document.security;

import org.springframework.data.mongodb.core.mapping.Document;

import com.am.common.amcommondata.document.base.BaseDocument;
import com.am.common.amcommondata.document.security.metadata.CompanyInfo;
import com.am.common.amcommondata.document.security.metadata.KeyInfo;
import com.am.common.amcommondata.document.security.metadata.SecurityMetadata;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

@Data
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "securities")
public class SecurityDocument extends BaseDocument {
    private KeyInfo key;
    private SecurityMetadata metadata;
    private CompanyInfo companyInfo;
}
