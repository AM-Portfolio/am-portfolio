package com.am.common.amcommondata.mapper;

import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.am.common.amcommondata.document.asset.AssetDocument;
import com.am.common.amcommondata.document.common.AuditMetadata;
import com.am.common.amcommondata.document.portfolio.HoldingAllocationDocument;
import com.am.common.amcommondata.document.portfolio.PortfolioDocument;
import com.am.common.amcommondata.mapper.asset.EquityMapper;
import com.am.common.amcommondata.mapper.asset.GenericAssetMapper;
import com.am.common.amcommondata.model.HoldingAllocation;
import com.am.common.amcommondata.model.PortfolioModelV1;
import com.am.common.amcommondata.model.asset.AssetModel;
import com.am.common.amcommondata.model.enums.Currency;
import com.am.common.amcommondata.model.enums.PortfolioKind;

@Component
public class PortfolioMapper {

    @Autowired
    private EquityMapper equityMapper;

    @Autowired
    private GenericAssetMapper assetMapper;

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

        List<AssetModel> mutualFunds = mapAssetsToModel(document.getMutualFunds());
        List<AssetModel> bonds = mapAssetsToModel(document.getBonds());
        List<AssetModel> commodities = mapAssetsToModel(document.getCommodities());
        List<AssetModel> cash = mapAssetsToModel(document.getCash());
        int equityCount = document.getEquities() != null ? document.getEquities().size() : 0;

        PortfolioModelV1 model = PortfolioModelV1.builder()
                .id(parseOrGenerateUUID(document.getId()))
                .name(document.getName())
                .description(document.getDescription())
                .owner(document.getOwner())
                .currency(document.getCurrency() != null ? document.getCurrency().name() : null)
                .fundType(document.getFundType())
                .notes(document.getNotes())
                .equityModels(document.getEquities() != null
                    ? document.getEquities().stream()
                        .map(equityMapper::toModel)
                        .collect(Collectors.toList())
                    : null)
                .mutualFunds(mutualFunds)
                .bonds(bonds)
                .commodities(commodities)
                .cash(cash)
                .totalValue(document.getTotalValue())
                .brokerType(document.getBrokerType())
                .portfolioKind(PortfolioKind.orBroker(document.getPortfolioKind()))
                .allocations(document.getAllocations() != null
                        ? document.getAllocations().stream().map(this::toAllocationModel).collect(Collectors.toList())
                        : null)
                .assetCount(equityCount + mutualFunds.size() + bonds.size() + commodities.size() + cash.size())
                .build();

        BasketPortfolioMapper.applyBasketFieldsToModel(model, document);

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
                .notes(model.getNotes())
                .equities(model.getEquityModels() != null
                    ? model.getEquityModels().stream()
                        .map(equityMapper::toDocument)
                        .collect(Collectors.toList())
                    : null)
                .mutualFunds(mapAssetsToDocument(model.getMutualFunds()))
                .bonds(mapAssetsToDocument(model.getBonds()))
                .commodities(mapAssetsToDocument(model.getCommodities()))
                .cash(mapAssetsToDocument(model.getCash()))
                .totalValue(model.getTotalValue())
                .brokerType(model.getBrokerType())
                .portfolioKind(PortfolioKind.orBroker(model.getPortfolioKind()))
                .allocations(model.getAllocations() != null
                        ? model.getAllocations().stream().map(this::toAllocationDocument).collect(Collectors.toList())
                        : null)
                .build();

        document.setCurrency(model.getCurrency() != null ? Currency.valueOf(model.getCurrency()) : null);

        boolean isNew = (model.getVersion() == null || model.getId() == null);
        long auditVersion = model.getVersion() != null ? model.getVersion() : 1L;
        document.setAudit(AuditMetadata.builder()
                .createdAt(model.getCreatedAt() != null ? model.getCreatedAt() : java.time.LocalDateTime.now())
                .createdBy(model.getCreatedBy())
                .updatedAt(model.getUpdatedAt())
                .updatedBy(model.getUpdatedBy())
                .version(auditVersion)
                .lastAction(isNew ? "CREATE" : "UPDATE")
                .build());

        BasketPortfolioMapper.applyBasketFieldsToDocument(document, model);

        return document;
    }

    private List<AssetModel> mapAssetsToModel(List<AssetDocument> docs) {
        if (docs == null || docs.isEmpty()) {
            return Collections.emptyList();
        }
        return docs.stream().map(assetMapper::toModel).collect(Collectors.toList());
    }

    private List<AssetDocument> mapAssetsToDocument(List<AssetModel> models) {
        if (models == null || models.isEmpty()) {
            return Collections.emptyList();
        }
        return models.stream().map(assetMapper::toDocument).collect(Collectors.toList());
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
