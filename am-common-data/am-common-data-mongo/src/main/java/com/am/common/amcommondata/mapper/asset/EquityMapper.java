package com.am.common.amcommondata.mapper.asset;

import org.springframework.stereotype.Component;

import com.am.common.amcommondata.document.asset.equity.EquityDocument;
import com.am.common.amcommondata.model.asset.equity.EquityModel;

@Component
public class EquityMapper extends AssetMapper<EquityModel, EquityDocument> {

    @Override
    protected EquityModel createModel() {
        return EquityModel.builder().build();
    }

    @Override
    protected EquityDocument createDocument() {
        return EquityDocument.builder().build();
    }

    @Override
    protected void populateModel(EquityModel model, EquityDocument document) {
        super.populateModel(model, document);
        model.setIsin(document.getIsin());
        model.setCompanyName(document.getCompanyName());
        model.setSector(document.getSector());
        model.setIndustry(document.getIndustry());
        model.setMarketCap(document.getMarketCap());
        model.setExchange(document.getExchange());
        model.setPeRatio(document.getPeRatio());
        model.setPbRatio(document.getPbRatio());
        model.setDividendYield(document.getDividendYield());
        model.setEps(document.getEps());
        model.setSharesOutstanding(document.getSharesOutstanding());
        model.setStockType(document.getStockType());
        model.setCountryOfIncorporation(document.getCountryOfIncorporation());
    }

    @Override
    protected void populateDocument(EquityDocument document, EquityModel model) {
        super.populateDocument(document, model);
        document.setIsin(model.getIsin());
        document.setCompanyName(model.getCompanyName());
        document.setSector(model.getSector());
        document.setIndustry(model.getIndustry());
        document.setMarketCap(model.getMarketCap());
        document.setExchange(model.getExchange());
        document.setPeRatio(model.getPeRatio());
        document.setPbRatio(model.getPbRatio());
        document.setDividendYield(model.getDividendYield());
        document.setEps(model.getEps());
        document.setSharesOutstanding(model.getSharesOutstanding());
        document.setStockType(model.getStockType());
        document.setCountryOfIncorporation(model.getCountryOfIncorporation());
    }
}
