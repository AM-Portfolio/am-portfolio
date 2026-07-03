package com.portfolio.model.mapper;

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
import com.portfolio.model.events.PortfolioUpdateEvent;
import com.portfolio.model.portfolio.v1.BrokerPortfolioSummary;

@Component
public class PortfolioMapperv1 {

    public PortfolioModelV1 toPortfolioModelV1(PortfolioUpdateEvent portfolioEvent) {

        PortfolioModelV1 portfolioModel = PortfolioModelV1.builder()
                .id(portfolioEvent.getId())
                .name(portfolioEvent.getPortfolioId())
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

    public PortfolioModelV1 toPortfolioModelV1(com.portfolio.model.events.trade.TradePortfolioSyncEvent tradeEvent) {
        String action = null;
        List<EquityModel> equityModels = new ArrayList<>();
        if (tradeEvent.getEquities() != null && !tradeEvent.getEquities().isEmpty()) {
            action = tradeEvent.getEquities().get(0).getAction();
            equityModels = tradeEvent.getEquities().stream().map(e -> {
                EquityModel em = new EquityModel();
                em.setSymbol(e.getSymbol());
                em.setIsin(e.getIsin());
                // In am-portfolio, the asset type might need mapping, but setting it safely
                em.setAssetType(AssetType.EQUITY);
                
                // Map quantities based on BUY/SELL
                if ("SELL".equalsIgnoreCase(e.getAction())) {
                    em.setQuantity(e.getSellQuantity() != null ? e.getSellQuantity().doubleValue() : 0.0);
                    em.setAvgBuyingPrice(e.getSellPrice() != null ? e.getSellPrice().doubleValue() : 0.0);
                } else {
                    em.setQuantity(e.getQuantity() != null ? e.getQuantity().doubleValue() : 0.0);
                    em.setAvgBuyingPrice(e.getAvgBuyingPrice() != null ? e.getAvgBuyingPrice().doubleValue() : 0.0);
                }
                return em;
            }).collect(Collectors.toList());
        }

        BrokerType brokerType = null;
        if (tradeEvent.getBrokerType() != null) {
            try {
                brokerType = BrokerType.valueOf(tradeEvent.getBrokerType().toUpperCase());
            } catch (Exception ignored) {}
        }

        return PortfolioModelV1.builder()
                .name(tradeEvent.getId()) // Name holds the portfolioId (which maps to Trade Event ID)
                .owner(tradeEvent.getUserId())
                .brokerType(brokerType)
                .fundType(FundType.DEFAULT)
                .status("Active")
                .createdBy(tradeEvent.getUserId())
                .equityModels(equityModels)
                .assetCount(calculateAssetCount(equityModels))
                .totalValue(calculateTotalValue(equityModels))
                .version(0L)
                .lastTradeAction(action)
                .build();
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
                    .filter(e -> e.getIsin() != null && e.getSymbol() != null) // @todo all values symbol, isin, name
                                                                               // shoudl comes from PortfolioUpdateEvent
                                                                               // . Remove filter in upcoming release
                    .map(e -> mapEquityModelToAsset(e, brokerType))
                    .collect(Collectors.toSet());
            assets.addAll(assetModels);
        }

        // if (mutualFunds != null) {
        // var fundModels = mutualFunds.stream()
        // .filter(e -> e.getIsin() != null && e.getSymbol() != null) // @todo all value
        // shoudl comes from PortfolioUpdateEvent . Remove filter in upcoming release
        // .map(e -> mapToAsset(e, brokerType))
        // .collect(Collectors.toSet());
        // assets.addAll(fundModels);
        // }

        return assets;
    }

    private Double calculateTotalValue(List<EquityModel> equityModels) {
        if (equityModels == null || equityModels.isEmpty()) {
            return 0.0;
        }
        return equityModels.stream()
                .filter(equity -> equity != null)
                .map(equity -> {
                    Double price = equity.getAvgBuyingPrice() != null ? equity.getAvgBuyingPrice() : 0.0;
                    Double quantity = equity.getQuantity() != null ? equity.getQuantity() : 0.0;
                    return price * quantity;
                })
                .reduce(0.0, Double::sum);
    }

    private Integer calculateAssetCount(List<EquityModel> equityModels) {
        if (equityModels == null || equityModels.isEmpty()) {
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
                .isin(equityModel.getIsin())
                .avgBuyingPrice(equityModel.getAvgBuyingPrice())
                .currentPrice(equityModel.getCurrentPrice())
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
