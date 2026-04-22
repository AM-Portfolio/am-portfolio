package com.am.common.amcommondata.mapper;

import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.am.common.amcommondata.document.common.AuditMetadata;
import com.am.common.amcommondata.document.portfolio.PortfolioDocument;
import com.am.common.amcommondata.mapper.asset.EquityMapper;
import com.am.common.amcommondata.model.PortfolioModelV1;
import com.am.common.amcommondata.model.enums.Currency;

@Component
public class PortfolioMapper {

    @Autowired
    private EquityMapper equityMapper;

    public PortfolioModelV1 toModel(PortfolioDocument document) {
        if (document == null) {
            return null;
        }

        PortfolioModelV1 model = PortfolioModelV1.builder()
                .id(UUID.fromString(document.getId()))
                .name(document.getName())
                .description(document.getDescription())
                .owner(document.getOwner())
                .currency(document.getCurrency() != null ? document.getCurrency().name() : null)
                .fundType(document.getFundType())
                //.status(document.getPortfolioStatus() != null ? document.getPortfolioStatus().name() : null)
                //.tags(document.getTags() != null ? document.getTags().stream().map(String::valueOf).collect(Collectors.toList()) : null)
                .notes(document.getNotes())
                .equityModels(document.getEquities() != null 
                    ? document.getEquities().stream()
                        .map(equityMapper::toModel)
                        .collect(Collectors.toList())
                    : null)
                .totalValue(document.getTotalValue())
                .brokerType(document.getBrokerType())
                .assetCount(document.getEquities() != null ? document.getEquities().size() : 0)
                .build();

        if (document.getAudit() != null) {
            model.setCreatedAt(document.getAudit().getCreatedAt());
            model.setCreatedBy(document.getAudit().getCreatedBy());
            model.setUpdatedAt(document.getAudit().getUpdatedAt());
            model.setUpdatedBy(document.getAudit().getUpdatedBy());
            model.setVersion(document.getAudit().getVersion());
        }

        return model;
    }

    public PortfolioDocument toDocument(PortfolioModelV1 model) {
        if (model == null) {
            return null;
        }

        PortfolioDocument document = PortfolioDocument.builder()
                .id(model.getId().toString())
                .name(model.getName())
                .description(model.getDescription())
                .owner(model.getOwner())
                .fundType(model.getFundType())
                //.tags(model.getTags())
                .notes(model.getNotes())
                .equities(model.getEquityModels() != null 
                    ? model.getEquityModels().stream()
                        .map(equityMapper::toDocument)
                        .collect(Collectors.toList())
                    : null)
                .totalValue(model.getTotalValue())
                .brokerType(model.getBrokerType())
                .build();

        // Set enums using helper methods
        document.setCurrency(model.getCurrency() != null ? Currency.valueOf(model.getCurrency()) : null);
        //document.setPortfolioStatus(model.getStatus() != null ? PortfolioStatus.valueOf(model.getStatus()) : null);
        //document.setBaseStatus(DocumentStatus.ACTIVE); // Default status for new documents

        document.setAudit(AuditMetadata.builder()
                .createdAt(model.getCreatedAt())
                .createdBy(model.getCreatedBy())
                .updatedAt(model.getUpdatedAt())
                .updatedBy(model.getUpdatedBy())
                .version(model.getVersion())
                .build());

        return document;
    }
}
