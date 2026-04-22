package com.am.common.amcommondata.document.security.metadata;

import java.time.LocalDateTime;

import org.springframework.data.mongodb.core.mapping.Field;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CompanyInfo {
    
    @Field("legal_name")
    private String legalName;
    
    @Field("symbol")
    private String symbol;
    
    @Field("isin")
    private String isin;
    
    @Field("cin")
    private String cin;
    
    @Field("pan")
    private String pan;
    
    @Field("website")
    private String website;
    
    @Field("email")
    private String email;
    
    @Field("phone_number")
    private String phoneNumber;
    
    @Field("description")
    private String description;
    
    @Field("business_description")
    private String businessDescription;
}
