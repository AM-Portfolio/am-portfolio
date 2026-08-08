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
        List<EquityModel> mappedEquities = mapToEquityModels(portfolioEvent, portfolioEvent.getBrokerType());

        PortfolioModelV1 portfolioModel = PortfolioModelV1.builder()
                .id(portfolioEvent.getId())
                .name(portfolioEvent.getPortfolioId())
                .owner(portfolioEvent.getUserId())
                .brokerType(portfolioEvent.getBrokerType())
                .fundType(FundType.DEFAULT)
                .status("Active")
                .createdBy(portfolioEvent.getUserId())
                .equityModels(mappedEquities)
                .assetCount(calculateAssetCount(mappedEquities))
                .totalValue(calculateTotalValue(mappedEquities))
                .version(0L)
                .build();
        return portfolioModel;
    }

    public PortfolioModelV1 toPortfolioModelV1(com.portfolio.model.events.trade.TradePortfolioSyncEvent tradeEvent) {
        String action = tradeEvent.getAction();
        
        List<EquityModel> equityModels = new ArrayList<>();
        if (tradeEvent.getEquities() != null && !tradeEvent.getEquities().isEmpty()) {
            if (action == null) {
                action = tradeEvent.getEquities().get(0).getAction();
            }
            equityModels = tradeEvent.getEquities().stream().map(e -> {
                EquityModel em = new EquityModel();
                em.setSymbol(e.getSymbol());
                em.setIsin(e.getIsin());
                // Map the sector, industry and market cap perfectly as received from kafka
                em.setSector(e.getSector());
                em.setIndustry(e.getIndustry());
                em.setMarketCap(e.getMarketCap());
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

        if (Boolean.TRUE.equals(tradeEvent.getDeleteAllTrades())) {
            action = "DELETE_PORTFOLIO";
        }

        BrokerType brokerType = resolveBrokerType(tradeEvent.getBrokerType());

        java.util.UUID portfolioUuid = null;
        if (tradeEvent.getId() != null) {
            try {
                portfolioUuid = java.util.UUID.fromString(tradeEvent.getId());
            } catch (IllegalArgumentException e) {
                // Non-UUID identifiers are tolerated; the ID is resolved downstream by name.
                portfolioUuid = null;
            }
        }

        return PortfolioModelV1.builder()
                .id(portfolioUuid)
                .name(tradeEvent.getPortfolioId() != null ? tradeEvent.getPortfolioId() : tradeEvent.getId())
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
        List<EquityModel> assets = new ArrayList<>();

        if (equities != null) {
            var assetModels = equities.stream()
                    .filter(e -> e != null)
                    .map(e -> mapEquityModelToAsset(e, brokerType))
                    .collect(Collectors.toList());
            assets.addAll(assetModels);
        }

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
        // Fallback for symbol: symbol -> upstock_instruments lookup -> isin -> name
        String resolvedSymbol = equityModel.getSymbol();
        if (resolvedSymbol == null || resolvedSymbol.isBlank() || (resolvedSymbol.length() == 12 && resolvedSymbol.matches("[A-Z]{2}[A-Z0-9]{10}"))) {
            String isinToResolve = equityModel.getIsin() != null ? equityModel.getIsin() : resolvedSymbol;
            if (isinToResolve != null && !isinToResolve.isBlank()) {
                try {
                    org.springframework.data.mongodb.core.MongoTemplate mongoTemplate = 
                            com.am.marketdata.common.util.ApplicationContextProvider.getBean(org.springframework.data.mongodb.core.MongoTemplate.class);
                    if (mongoTemplate != null) {
                        org.springframework.data.mongodb.core.query.Query q = new org.springframework.data.mongodb.core.query.Query(
                                org.springframework.data.mongodb.core.query.Criteria.where("isin").is(isinToResolve.trim().toUpperCase())
                        );
                        com.am.marketdata.common.model.UpstoxInstrument inst = mongoTemplate.findOne(q, com.am.marketdata.common.model.UpstoxInstrument.class);
                        if (inst != null && inst.getTradingSymbol() != null && !inst.getTradingSymbol().isBlank()) {
                            resolvedSymbol = inst.getTradingSymbol().trim().toUpperCase();
                        }
                    }
                } catch (Exception ignored) {}
            }
        }

        if (resolvedSymbol == null || resolvedSymbol.isBlank()) {
            if (equityModel.getIsin() != null && !equityModel.getIsin().isBlank()) {
                resolvedSymbol = equityModel.getIsin();
            } else if (equityModel.getName() != null && !equityModel.getName().isBlank()) {
                resolvedSymbol = equityModel.getName();
            }
        }

        // Fallback for avgBuyingPrice: avgBuyingPrice -> investmentValue / quantity
        Double resolvedAvgPrice = equityModel.getAvgBuyingPrice();
        if ((resolvedAvgPrice == null || resolvedAvgPrice == 0.0)
                && equityModel.getInvestmentValue() != null 
                && equityModel.getQuantity() != null && equityModel.getQuantity() > 0) {
            resolvedAvgPrice = equityModel.getInvestmentValue() / equityModel.getQuantity();
        }

        Double computedInvestmentValue = equityModel.getInvestmentValue();
        if (computedInvestmentValue == null && resolvedAvgPrice != null && equityModel.getQuantity() != null) {
            computedInvestmentValue = resolvedAvgPrice * equityModel.getQuantity();
        }

        return EquityModel.builder()
                .assetType(AssetType.EQUITY)
                .brokerType(brokerType)
                .symbol(resolvedSymbol)
                .name(equityModel.getName())
                .companyName(equityModel.getCompanyName() != null ? equityModel.getCompanyName() : equityModel.getName())
                .isin(equityModel.getIsin())
                .avgBuyingPrice(resolvedAvgPrice)
                .currentPrice(equityModel.getCurrentPrice())
                .quantity(equityModel.getQuantity())
                .currentValue(equityModel.getCurrentValue())
                .investmentValue(computedInvestmentValue)
                .sector(equityModel.getSector())
                .industry(equityModel.getIndustry())
                .marketCap(equityModel.getMarketCap())
                .todayProfitLoss(equityModel.getTodayProfitLoss())
                .todayProfitLossPercentage(equityModel.getTodayProfitLossPercentage())
                .profitLoss(equityModel.getProfitLoss())
                .profitLossPercentage(equityModel.getProfitLossPercentage())
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

    /**
     * Trade publishes enum names (ZERODHA, GROW, UNKNOWN, …). Portfolio enum is a subset
     * (GROWW not GROW). Fall back via display code, then ZERODHA-safe null avoidance for create.
     */
    private BrokerType resolveBrokerType(String raw) {
        if (raw == null || raw.isBlank() || "UNKNOWN".equalsIgnoreCase(raw) || "OTHER".equalsIgnoreCase(raw)) {
            return null;
        }
        String normalized = raw.trim().toUpperCase().replace(' ', '_');
        if ("GROW".equals(normalized) || "GROWW".equals(normalized)) {
            return BrokerType.GROWW;
        }
        try {
            return BrokerType.valueOf(normalized);
        } catch (IllegalArgumentException ignored) {
            BrokerType fromCode = BrokerType.fromCode(raw.trim());
            return fromCode;
        }
    }
}
