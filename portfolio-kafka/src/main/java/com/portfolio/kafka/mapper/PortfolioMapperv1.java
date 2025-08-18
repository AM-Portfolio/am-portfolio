package com.portfolio.kafka.mapper;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

import com.am.common.amcommondata.model.PortfolioModelV1;
import com.am.common.amcommondata.model.asset.equity.EquityModel;
import com.am.common.amcommondata.model.asset.mutualfund.MutualFundModel;
import com.am.common.amcommondata.model.enums.AssetType;
import com.am.common.amcommondata.model.enums.BrokerType;
import com.am.common.amcommondata.model.enums.FundType;
import com.portfolio.kafka.model.PortfolioUpdateEvent;
import com.portfolio.model.portfolio.v1.BrokerPortfolioSummary;

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
        .assetCount(calculateAssetCount(portfolioEvent.getEquities()))
        .totalValue(calculateTotalValue(portfolioEvent.getEquities()))
        .version(0L)
        .build();
        return portfolioModel;
    }

    public BrokerPortfolioSummary toPortfolioModelV1(PortfolioModelV1 portfolio) {

        BrokerPortfolioSummary portfolioModel = BrokerPortfolioSummary.builder()
        .investmentValue(calculateTotalValue(portfolio.getEquityModels()))
        .totalAssets(portfolio.getAssetCount())
        .lastUpdated(portfolio.getUpdatedAt())
        .build();
        return portfolioModel;
    }

    private List<EquityModel> mapToEquityModels(PortfolioUpdateEvent portfolio, BrokerType brokerType) {
        List<EquityModel> equities = portfolio.getEquities();
        List<MutualFundModel> mutualFunds = portfolio.getMutualFunds();
        List<EquityModel> assets = new ArrayList<>();
        
        if (equities != null) {
            var assetModels = equities.stream()
            .filter(e -> e.getIsin() != null && e.getSymbol() != null )  // @todo all values symbol, isin, name shoudl comes from PortfolioUpdateEvent . Remove filter in upcoming release 
            .map(e -> mapEquityModelToAsset(e, brokerType))
            .collect(Collectors.toSet());
            assets.addAll(assetModels);
        }
        
        // if (mutualFunds != null) {
        //     var fundModels = mutualFunds.stream()
        //         .filter(e -> e.getIsin() != null && e.getSymbol() != null) // @todo all value shoudl comes from PortfolioUpdateEvent . Remove filter in upcoming release 
        //         .map(e -> mapToAsset(e, brokerType))
        //         .collect(Collectors.toSet());
        //     assets.addAll(fundModels);
        // }

        return assets;
    }

    private Double calculateTotalValue(List<EquityModel> equityModels) {
        if (equityModels.isEmpty()) {
            return 0.0;
        }
        return equityModels.stream()
        .map(equity -> equity.getAvgBuyingPrice() * equity.getQuantity())
        .reduce(0.0, Double::sum);
    }

    private Integer calculateAssetCount(List<EquityModel> equityModels) {
        if (equityModels.isEmpty()) {
            return 0;
        }
        return equityModels.size();
    }

    private EquityModel mapEquityModelToAsset(EquityModel equityModel, BrokerType brokerType) {
        return EquityModel.builder()
        .assetType(AssetType.EQUITY)
        .brokerType(brokerType)
        .symbol(equityModel.getSymbol())
        .name(equityModel.getName())
        .avgBuyingPrice(equityModel.getAvgBuyingPrice())
        .quantity(equityModel.getQuantity())
        .build();
    }

    private MutualFundModel mapToAsset(MutualFundModel fundModel, BrokerType brokerType) {
        return MutualFundModel.builder()
        .assetType(AssetType.MUTUAL_FUND)
        .brokerType(brokerType)
        .symbol(fundModel.getSymbol())
        .name(fundModel.getName())
        .avgBuyingPrice(fundModel.getAvgBuyingPrice())
        .quantity(fundModel.getQuantity())
        .build();
    }
}
