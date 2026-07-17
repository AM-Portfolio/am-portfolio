package com.am.common.amcommondata.mapper.asset;

import org.springframework.stereotype.Component;

import com.am.common.amcommondata.document.asset.mutualfund.MutualFundDocument;
import com.am.common.amcommondata.model.asset.mutualfund.MutualFundModel;
import com.am.common.amcommondata.model.enums.AssetType;

@Component
public class MutualFundMapper {

    public MutualFundModel toModel(MutualFundDocument document) {
        if (document == null) {
            return null;
        }

        return MutualFundModel.builder()
                .assetType(AssetType.MUTUAL_FUND)
                .brokerType(document.getBrokerType())
                .isin(document.getIsin())
                .symbol(document.getSymbol())
                .name(document.getName())
                .fundHouse(document.getFundHouse())
                .category(document.getCategory())
                .subCategory(document.getSubCategory())
                .schemeType(document.getSchemeType())
                .aum(document.getAum())
                .nav(document.getNav())
                .expenseRatio(document.getExpenseRatio())
                .exitLoad(document.getExitLoad())
                .minInvestment(document.getMinInvestment())
                .fundManager(document.getFundManager())
                .inceptionDate(document.getInceptionDate())
                .returnOneYear(document.getReturnOneYear())
                .returnThreeYear(document.getReturnThreeYear())
                .returnFiveYear(document.getReturnFiveYear())
                .riskLevel(document.getRiskLevel())
                .investmentStrategy(document.getInvestmentStrategy())
                .benchmarkIndex(document.getBenchmarkIndex())
                .directPlan(document.getDirectPlan())
                .sipFrequency(document.getSipFrequency())
                .sipMinimumAmount(document.getSipMinimumAmount())
                .avgBuyingPrice(document.getAvgBuyingPrice())
                .currentPrice(document.getCurrentPrice())
                .quantity(document.getQuantity())
                .currentValue(document.getCurrentValue())
                .build();
    }

    public MutualFundDocument toDocument(MutualFundModel model) {
        if (model == null) {
            return null;
        }

        return MutualFundDocument.builder()
                .brokerType(model.getBrokerType())
                .isin(model.getIsin())
                .symbol(model.getSymbol())
                .name(model.getName())
                .fundHouse(model.getFundHouse())
                .category(model.getCategory())
                .subCategory(model.getSubCategory())
                .schemeType(model.getSchemeType())
                .aum(model.getAum())
                .nav(model.getNav())
                .expenseRatio(model.getExpenseRatio())
                .exitLoad(model.getExitLoad())
                .minInvestment(model.getMinInvestment())
                .fundManager(model.getFundManager())
                .inceptionDate(model.getInceptionDate())
                .returnOneYear(model.getReturnOneYear())
                .returnThreeYear(model.getReturnThreeYear())
                .returnFiveYear(model.getReturnFiveYear())
                .riskLevel(model.getRiskLevel())
                .investmentStrategy(model.getInvestmentStrategy())
                .benchmarkIndex(model.getBenchmarkIndex())
                .directPlan(model.getDirectPlan())
                .sipFrequency(model.getSipFrequency())
                .sipMinimumAmount(model.getSipMinimumAmount())
                .avgBuyingPrice(model.getAvgBuyingPrice())
                .currentPrice(model.getCurrentPrice())
                .quantity(model.getQuantity())
                .currentValue(model.getCurrentValue())
                .build();
    }
}
