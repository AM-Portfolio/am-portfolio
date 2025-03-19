package com.portfolio.mapper;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

import com.am.common.amcommondata.model.PortfolioModel;
import com.am.common.amcommondata.model.PortfolioModelV1;
import com.am.common.amcommondata.model.asset.AssetModel;
import com.am.common.amcommondata.model.asset.equity.EquityModel;
import com.am.common.amcommondata.model.asset.mutualfund.MutualFundModel;
import com.am.common.amcommondata.model.enums.AssetType;
import com.am.common.amcommondata.model.enums.BrokerType;
import com.am.common.amcommondata.model.enums.FundType;
import com.portfolio.kafka.model.PortfolioUpdateEvent;

@Component
public class PortfolioMapperv1 {

    public PortfolioModelV1 toPortfolioModelV1(PortfolioUpdateEvent portfolioEvent) {

        PortfolioModelV1 portfolioModel = PortfolioModelV1.builder()
        .id(portfolioEvent.getId())
        .name("Default Portfolio")
        .owner(portfolioEvent.getUserId())
        .brokerType(portfolioEvent.getBrokerType())
        .fundType(FundType.DEFAULT)
        .status("Active")
        .createdBy(portfolioEvent.getUserId())
        .equityModels(portfolioEvent.getEquities())
        //.assetCount(calculateAssetCount(assets))
        //.totalValue(calculateTotalValue(assets))
        .version(0L)
        .build();
        return portfolioModel;
    }

    public PortfolioModel toPortfolioModel(PortfolioUpdateEvent portfolioEvent) {
        var assets = mapToAssets(portfolioEvent, portfolioEvent.getBrokerType());

        PortfolioModel portfolioModel = PortfolioModel.builder()
        .id(portfolioEvent.getId())
        .name("Default Portfolio")
        .owner(portfolioEvent.getUserId())
        .brokerType(portfolioEvent.getBrokerType())
        .fundType(FundType.DEFAULT)
        .status("Active")
        .createdBy(portfolioEvent.getUserId())
        .assets(assets)
        //.assetCount(calculateAssetCount(assets))
        //.totalValue(calculateTotalValue(assets))
        .version(0L)
        .build();
        return portfolioModel;
    }

    private Set<AssetModel> mapToAssets(PortfolioUpdateEvent portfolio, BrokerType brokerType) {
        List<EquityModel> equities = portfolio.getEquities();
        List<MutualFundModel> mutualFunds = portfolio.getMutualFunds();
        Set<AssetModel> assets = new HashSet<>();
        
        if (equities != null) {
            var assetModels = equities.stream()
            .filter(e -> e.getIsin() != null && e.getSymbol() != null )  // @todo all values symbol, isin, name shoudl comes from PortfolioUpdateEvent . Remove filter in upcoming release 
            .map(e -> mapEquityModelToAsset(e, brokerType))
            .collect(Collectors.toSet());
            assets.addAll(assetModels);
        }
        
        if (mutualFunds != null) {
            var fundModels = mutualFunds.stream()
                .filter(e -> e.getIsin() != null && e.getSymbol() != null) // @todo all value shoudl comes from PortfolioUpdateEvent . Remove filter in upcoming release 
                .map(e -> mapToAsset(e, brokerType))
                .collect(Collectors.toSet());
            assets.addAll(fundModels);
        }

        return assets;
    }

    // private Double calculateTotalValue(Set<AssetModel> assets) {
    //     if (assets.isEmpty()) {
    //         return 0.0;
    //     }
    //     return assets.stream()
    //     .map(asset -> asset.getAvgBuyingPrice() * asset.getQuantity())
    //     .reduce(0.0, Double::sum);
    // }

    // private Integer calculateAssetCount(Set<AssetModel> assets) {
    //     if (assets.isEmpty()) {
    //         return 0;
    //     }
    //     return assets.size();
    // }

    private AssetModel mapEquityModelToAsset(EquityModel equityModel, BrokerType brokerType) {
        return AssetModel.builder()
        .assetType(AssetType.EQUITY)
        .brokerType(brokerType)
        //.isin(equityModel.getIsin())
        .symbol(equityModel.getSymbol())
        //.sector(equityModel.getSector())
        .name(equityModel.getName())
        .avgBuyingPrice(equityModel.getAvgBuyingPrice())
        .name(equityModel.getName())
        .quantity(equityModel.getQuantity())
        .build();
    }

    private AssetModel mapToAsset(MutualFundModel fundModel, BrokerType brokerType) {
        return AssetModel.builder()
        .assetType(AssetType.MUTUAL_FUND)
        //.isin(fundModel.getIsin())
        .symbol(fundModel.getSymbol())
        .name(fundModel.getName())
        .brokerType(brokerType)
        .avgBuyingPrice(fundModel.getAvgBuyingPrice())
        .name(fundModel.getName())
        .quantity(fundModel.getQuantity())
        .build();
    }
}
