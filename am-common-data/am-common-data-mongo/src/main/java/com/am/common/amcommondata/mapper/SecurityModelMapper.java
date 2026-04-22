package com.am.common.amcommondata.mapper;

import java.util.UUID;

import org.springframework.stereotype.Component;

import com.am.common.amcommondata.document.common.AuditMetadata;
import com.am.common.amcommondata.document.security.SecurityDocument;
import com.am.common.amcommondata.document.security.metadata.CompanyInfo;
import com.am.common.amcommondata.document.security.metadata.KeyInfo;
import com.am.common.amcommondata.document.security.metadata.SecurityMetadata;
import com.am.common.amcommondata.model.AuditModel;
import com.am.common.amcommondata.model.MarketCapType;
import com.am.common.amcommondata.model.security.CompanyInfoModel;
import com.am.common.amcommondata.model.security.SecurityKeyModel;
import com.am.common.amcommondata.model.security.SecurityMetadataModel;
import com.am.common.amcommondata.model.security.SecurityModel;

@Component
public class SecurityModelMapper {

    public SecurityModel toModel(SecurityDocument document) {
        if (document == null) {
            return null;
        }

        SecurityModel model = SecurityModel.builder()
                //.status(document.getStatus())
                .audit(toAuditModel(document.getAudit()))
                .key(toKeyModel(document.getKey()))
                .metadata(toMetadataModel(document.getMetadata()))
                .companyInfo(toCompanyInfoModel(document.getCompanyInfo()))
                .build();
        
        // Set ID after building to avoid UUID encoding issues
        model.setId(UUID.fromString(document.getId()));
        return model;
    }

    public SecurityDocument toDocument(SecurityModel model) {
        if (model == null) {
            return null;
        }

        SecurityDocument document = SecurityDocument.builder()
                //.status(model.getStatus())
                .audit(toAuditMetadata(model.getAudit()))
                .key(toKeyInfo(model.getKey()))
                .metadata(toSecurityMetadata(model.getMetadata()))
                .companyInfo(toCompanyInfo(model.getCompanyInfo()))
                .build();
        
        // Set ID after building to avoid UUID encoding issues
        document.setId(model.getId().toString());
        return document;
    }

    private AuditModel toAuditModel(AuditMetadata metadata) {
        if (metadata == null) {
            return null;
        }

        return AuditModel.builder()
                .version(metadata.getVersion())
                .createdAt(metadata.getCreatedAt())
                .createdBy(metadata.getCreatedBy())
                .createdByEmail(metadata.getCreatedByEmail())
                .updatedAt(metadata.getUpdatedAt())
                .updatedBy(metadata.getUpdatedBy())
                .updatedByEmail(metadata.getUpdatedByEmail())
                .lastAction(metadata.getLastAction())
                .build();
    }

    private AuditMetadata toAuditMetadata(AuditModel model) {
        if (model == null) {
            return null;
        }

        return AuditMetadata.builder()
                .version(model.getVersion())
                .createdAt(model.getCreatedAt())
                .createdBy(model.getCreatedBy())
                .createdByEmail(model.getCreatedByEmail())
                .updatedAt(model.getUpdatedAt())
                .updatedBy(model.getUpdatedBy())
                .updatedByEmail(model.getUpdatedByEmail())
                .lastAction(model.getLastAction())
                .build();
    }

    private SecurityKeyModel toKeyModel(KeyInfo keyInfo) {
        if (keyInfo == null) {
            return null;
        }

        return SecurityKeyModel.builder()
                .symbol(keyInfo.getSymbol())
                .isin(keyInfo.getIsin())
                .build();
    }

    private KeyInfo toKeyInfo(SecurityKeyModel model) {
        if (model == null) {
            return null;
        }

        return KeyInfo.builder()
                .symbol(model.getSymbol())
                .isin(model.getIsin())
                .build();
    }

    private SecurityMetadataModel toMetadataModel(SecurityMetadata metadata) {
        if (metadata == null) {
            return null;
        }

        return SecurityMetadataModel.builder()
                .securityName(metadata.getSecurityName())
                .sector(metadata.getSector())
                .industry(metadata.getIndustry())
                .marketCapValue(metadata.getMarketCapValue())
                .marketCapType(metadata.getMarketCapType())
                .securityType(metadata.getSecurityType())
                .exchange(metadata.getExchange())
                .countryOfIncorporation(metadata.getCountryOfIncorporation())
                .listingDate(metadata.getListingDate())
                .delistingDate(metadata.getDelistingDate())
                .tradingStatus(metadata.getTradingStatus())
                .customAttributes(metadata.getCustomAttributes())
                .build();
    }

    private SecurityMetadata toSecurityMetadata(SecurityMetadataModel model) {
        if (model == null) {
            return null;
        }

        return SecurityMetadata.builder()
                .securityName(model.getSecurityName())
                .sector(model.getSector())
                .industry(model.getIndustry())
                .marketCapValue(model.getMarketCapValue())
                .marketCapType(getMarketCapType(model.getMarketCapValue()))
                .securityType(model.getSecurityType())
                .exchange(model.getExchange())
                .countryOfIncorporation(model.getCountryOfIncorporation())
                .listingDate(model.getListingDate())
                .delistingDate(model.getDelistingDate())
                .tradingStatus(model.getTradingStatus())
                .customAttributes(model.getCustomAttributes())
                .build();
    }

    private CompanyInfoModel toCompanyInfoModel(CompanyInfo companyInfo) {
        if (companyInfo == null) {
            return null;
        }

        return CompanyInfoModel.builder()
                .legalName(companyInfo.getLegalName())
                .symbol(companyInfo.getSymbol())
                .isin(companyInfo.getIsin())
                .cin(companyInfo.getCin())
                .pan(companyInfo.getPan())
                .website(companyInfo.getWebsite())
                .email(companyInfo.getEmail())
                .phoneNumber(companyInfo.getPhoneNumber())
                .description(companyInfo.getDescription())
                .businessDescription(companyInfo.getBusinessDescription())
                .build();
    }

    private CompanyInfo toCompanyInfo(CompanyInfoModel model) {
        if (model == null) {
            return null;
        }

        return CompanyInfo.builder()
                .legalName(model.getLegalName())
                .symbol(model.getSymbol())
                .isin(model.getIsin())
                .cin(model.getCin())
                .pan(model.getPan())
                .website(model.getWebsite())
                .email(model.getEmail())
                .phoneNumber(model.getPhoneNumber())
                .description(model.getDescription())
                .businessDescription(model.getBusinessDescription())
                .build();
    }

    public MarketCapType getMarketCapType(Double marketCapValue) {
        if (marketCapValue != null && marketCapValue > 0) {
            // Calculate market cap type based on market cap value
            if (marketCapValue >= 1000000000000D) { // > 1 lakh crore
                return MarketCapType.LARGE_CAP;
            } else if (marketCapValue >= 250000000000D) { // > 25k crore
                return MarketCapType.MID_CAP;
            } else if (marketCapValue >= 50000000000D) { // > 5k crore
                return MarketCapType.SMALL_CAP;
            } else {
                return MarketCapType.MICRO_CAP;
            }
        } else {
            return MarketCapType.MICRO_CAP;
        }
    }
}
