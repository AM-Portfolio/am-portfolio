package com.portfolio.mapper;

import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

import com.am.common.amcommondata.model.PortfolioModel;
import com.am.common.amcommondata.model.asset.AssetModel;
import com.am.common.amcommondata.model.asset.equity.EquityModel;
import com.am.common.amcommondata.model.asset.mutualfund.MutualFundModel;
import com.am.common.amcommondata.model.enums.FundType;
import com.portfolio.kafka.model.PortfolioUpdateEvent;

@Component
public class PortfolioMapper {

    public PortfolioModel toPortfolioModel(PortfolioUpdateEvent portfolioEvent) {
        PortfolioModel portfolioModel = PortfolioModel.builder()
        .id(portfolioEvent.getId())
        .owner(portfolioEvent.getUserId())
        .fundType(FundType.DEFAULT)
        .status("Active")
        .createdBy(portfolioEvent.getUserId())
        .assets(mapToAssets(portfolioEvent))
        .build();
        return portfolioModel;
    }



    private Set<AssetModel> mapToAssets(PortfolioUpdateEvent portfolio) {
        Set<EquityModel> equities = portfolio.getEquities();
        Set<MutualFundModel> mutualFunds = portfolio.getMutualFunds();
        Set<AssetModel> assets = new HashSet<>();
        
        if (equities != null) {
            assets.addAll(equities.stream()
                .map(equity -> (AssetModel) equity)
                .collect(Collectors.toSet()));
        }
        
        if (mutualFunds != null) {
            assets.addAll(mutualFunds.stream()
                .map(fund -> (AssetModel) fund)
                .collect(Collectors.toSet()));
        }
        
        return assets;
    }
}
