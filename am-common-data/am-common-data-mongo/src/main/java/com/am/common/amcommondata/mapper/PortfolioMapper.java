package com.am.common.amcommondata.mapper;

import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.am.common.amcommondata.document.common.AuditMetadata;
import com.am.common.amcommondata.document.portfolio.HoldingAllocationDocument;
import com.am.common.amcommondata.document.portfolio.PortfolioDocument;
import com.am.common.amcommondata.mapper.asset.EquityMapper;
import com.am.common.amcommondata.model.HoldingAllocation;
import com.am.common.amcommondata.model.PortfolioModelV1;
import com.am.common.amcommondata.model.enums.Currency;
import com.am.common.amcommondata.model.enums.PortfolioKind;

@Component
public class PortfolioMapper {

    @Autowired
    private EquityMapper equityMapper;

    private UUID parseOrGenerateUUID(String id) {
        if (id == null) return null;
        try {
            return UUID.fromString(id);
        } catch (IllegalArgumentException e) {
            return UUID.nameUUIDFromBytes(id.getBytes());
        }
    }

    public PortfolioModelV1 toModel(PortfolioDocument document) {
        if (document == null) {
            return null;
        }

        PortfolioModelV1 model = PortfolioModelV1.builder()
                .id(parseOrGenerateUUID(document.getId()))
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
                .portfolioKind(PortfolioKind.orBroker(document.getPortfolioKind()))
                .sourcePortfolioId(document.getSourcePortfolioId())
                .etfIsin(document.getEtfIsin())
                .etfName(document.getEtfName())
                .createdFromBasketAt(document.getCreatedFromBasketAt())
                .gapMissingCount(document.getGapMissingCount())
                .replicaScore(document.getReplicaScore())
                .allocations(document.getAllocations() != null
                        ? document.getAllocations().stream().map(this::toAllocationModel).collect(Collectors.toList())
                        : null)
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
                .id(model.getId() != null ? model.getId().toString() : UUID.randomUUID().toString())
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
                .portfolioKind(PortfolioKind.orBroker(model.getPortfolioKind()))
                .sourcePortfolioId(model.getSourcePortfolioId())
                .etfIsin(model.getEtfIsin())
                .etfName(model.getEtfName())
                .createdFromBasketAt(model.getCreatedFromBasketAt())
                .gapMissingCount(model.getGapMissingCount())
                .replicaScore(model.getReplicaScore())
                .allocations(model.getAllocations() != null
                        ? model.getAllocations().stream().map(this::toAllocationDocument).collect(Collectors.toList())
                        : null)
                .build();

        // Set enums using helper methods
        document.setCurrency(model.getCurrency() != null ? Currency.valueOf(model.getCurrency()) : null);
        //document.setPortfolioStatus(model.getStatus() != null ? PortfolioStatus.valueOf(model.getStatus()) : null);
        //document.setBaseStatus(DocumentStatus.ACTIVE); // Default status for new documents

        boolean isNew = (model.getVersion() == null || model.getId() == null);
        long existingVersion = model.getVersion() != null ? model.getVersion() : 0L;
        document.setAudit(AuditMetadata.builder()
                .createdAt(model.getCreatedAt() != null ? model.getCreatedAt() : java.time.LocalDateTime.now())
                .createdBy(model.getCreatedBy())
                .updatedAt(model.getUpdatedAt())
                .updatedBy(model.getUpdatedBy())
                .version(existingVersion)
                .lastAction(isNew ? "CREATE" : "UPDATE")
                .build());

        return document;
    }

    private HoldingAllocation toAllocationModel(HoldingAllocationDocument doc) {
        if (doc == null) {
            return null;
        }
        return HoldingAllocation.builder()
                .basketPortfolioId(doc.getBasketPortfolioId())
                .isin(doc.getIsin())
                .symbol(doc.getSymbol())
                .quantity(doc.getQuantity())
                .build();
    }

    private HoldingAllocationDocument toAllocationDocument(HoldingAllocation model) {
        if (model == null) {
            return null;
        }
        return HoldingAllocationDocument.builder()
                .basketPortfolioId(model.getBasketPortfolioId())
                .isin(model.getIsin())
                .symbol(model.getSymbol())
                .quantity(model.getQuantity())
                .build();
    }
}
