package com.portfolio.mapper;

import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

import com.am.common.amcommondata.model.PortfolioModel;
import com.am.common.amcommondata.model.asset.AssetModel;
import com.am.common.amcommondata.model.asset.equity.EquityModel;
import com.am.common.amcommondata.model.asset.mutualfund.MutualFundModel;
import com.am.common.amcommondata.model.enums.AssetType;
import com.am.common.amcommondata.model.enums.FundType;
import com.portfolio.kafka.model.PortfolioUpdateEvent;

@Component
public class PortfolioMapper {

    public PortfolioModel toPortfolioModel(PortfolioUpdateEvent portfolioEvent) {
        PortfolioModel portfolioModel = PortfolioModel.builder()
        .id(portfolioEvent.getId())
        .name("Default Portfolio")
        .owner(portfolioEvent.getUserId())
        .fundType(FundType.DEFAULT)
        .status("Active")
        .createdBy(portfolioEvent.getUserId())
        .assets(mapToAssets(portfolioEvent))
        .version(0L)
        .build();
        return portfolioModel;
    }



    private Set<AssetModel> mapToAssets(PortfolioUpdateEvent portfolio) {
        Set<EquityModel> equities = portfolio.getEquities();
        Set<MutualFundModel> mutualFunds = portfolio.getMutualFunds();
        Set<AssetModel> assets = new HashSet<>();
        
        if (equities != null) {
            var assetModels = equities.stream()
            .filter(e -> e.getIsin() != null && e.getSymbol() != null)
            .map(this::mapEquityModelToAsset)
            .collect(Collectors.toSet());
            assets.addAll(assetModels);
        }
        
        if (mutualFunds != null) {
            var fundModels = mutualFunds.stream()
                .filter(e -> e.getIsin() != null && e.getSymbol() != null)
                .map(this::mapToAsset)
                .collect(Collectors.toSet());
            assets.addAll(fundModels);
        }

        return assets;
    }

    private AssetModel mapEquityModelToAsset(EquityModel equityModel) {
        return AssetModel.builder()
        .assetType(AssetType.EQUITY)
        .isin(equityModel.getIsin())
        .symbol(equityModel.getSymbol())
        .name(equityModel.getName())
        .avgBuyingPrice(equityModel.getAvgBuyingPrice())
        .name(equityModel.getName())
        .quantity(equityModel.getQuantity())
        .build();
    }

    private AssetModel mapToAsset(MutualFundModel fundModel) {
        return AssetModel.builder()
        .assetType(AssetType.MUTUAL_FUND)
        .isin(fundModel.getIsin())
        .symbol(fundModel.getSymbol())
        .name(fundModel.getName())
        .avgBuyingPrice(fundModel.getAvgBuyingPrice())
        .name(fundModel.getName())
        .quantity(fundModel.getQuantity())
        .build();
    }
}
