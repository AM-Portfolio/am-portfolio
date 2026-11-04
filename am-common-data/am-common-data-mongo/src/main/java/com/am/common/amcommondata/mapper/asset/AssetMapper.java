package com.am.common.amcommondata.mapper.asset;

import java.util.UUID;

import org.springframework.stereotype.Component;

import com.am.common.amcommondata.document.asset.AssetDocument;
import com.am.common.amcommondata.model.asset.AssetModel;
import com.am.common.amcommondata.model.asset.pension.NPSModel;

@Component
public abstract class AssetMapper<M extends AssetModel, D extends AssetDocument> {

    public M toModel(D document) {
        if (document == null) {
            return null;
        }

        M model = createModel();
        populateModel(model, document);
        return model;
    }

    public D toDocument(M model) {
        if (model == null) {
            return null;
        }

        D document = createDocument();
        populateDocument(document, model);
        return document;
    }

    protected abstract M createModel();
    protected abstract D createDocument();

    protected void populateModel(M model, D document) {
        model.setId(document.getId() != null ? UUID.fromString(document.getId()) : null);
        model.setSymbol(document.getSymbol());
        model.setName(document.getName());
        model.setDescription(document.getDescription());
        model.setAssetType(document.getAssetType());
        model.setMarketData(document.getMarketData());
        model.setQuantity(document.getQuantity());
        model.setAvgBuyingPrice(document.getAvgBuyingPrice());
        model.setCurrentPrice(document.getCurrentPrice());
        model.setCurrentValue(document.getCurrentValue());
        model.setInvestmentValue(document.getInvestmentValue());
        model.setImportMarkPrice(document.getImportMarkPrice());
        model.setBrokerType(document.getBrokerType());
        model.setProfitLoss(document.getProfitLoss());
        model.setProfitLossPercentage(document.getProfitLossPercentage());
        model.setCreatedAt(document.getCreatedAt());
        model.setUpdatedAt(document.getUpdatedAt());
        model.setIsActive(document.getIsActive());
        model.setAssetClass(document.getAssetClass());
        model.setIsin(document.getIsin());
        model.setSchemeCode(document.getSchemeCode());
        model.setSchemeClass(document.getSchemeClass());
        model.setSchemeOption(document.getSchemeOption());
        model.setStatementNav(document.getStatementNav());
        model.setLiveNav(document.getLiveNav());
        model.setIsinResolveStatus(document.getIsinResolveStatus());
        if (model instanceof NPSModel nps) {
            nps.setTier(document.getTier());
            nps.setPran(document.getPran());
        }
    }

    protected void populateDocument(D document, M model) {
        document.setId(model.getId() != null ? model.getId().toString() : null);
        document.setSymbol(model.getSymbol());
        document.setName(model.getName());
        document.setDescription(model.getDescription());
        document.setAssetType(model.getAssetType());
        document.setMarketData(model.getMarketData());
        document.setQuantity(model.getQuantity());
        document.setAvgBuyingPrice(model.getAvgBuyingPrice());
        document.setCurrentPrice(model.getCurrentPrice());
        document.setCurrentValue(model.getCurrentValue());
        document.setInvestmentValue(model.getInvestmentValue());
        document.setImportMarkPrice(model.getImportMarkPrice());
        document.setBrokerType(model.getBrokerType());
        document.setProfitLoss(model.getProfitLoss());
        document.setProfitLossPercentage(model.getProfitLossPercentage());
        document.setCreatedAt(model.getCreatedAt());
        document.setUpdatedAt(model.getUpdatedAt());
        document.setIsActive(model.getIsActive());
        document.setAssetClass(model.getAssetClass());
        document.setIsin(model.getIsin());
        document.setSchemeCode(model.getSchemeCode());
        document.setSchemeClass(model.getSchemeClass());
        document.setSchemeOption(model.getSchemeOption());
        document.setStatementNav(model.getStatementNav());
        document.setLiveNav(model.getLiveNav());
        document.setIsinResolveStatus(model.getIsinResolveStatus());
        if (model instanceof NPSModel nps) {
            document.setTier(nps.getTier());
            document.setPran(nps.getPran());
        }
    }
}
