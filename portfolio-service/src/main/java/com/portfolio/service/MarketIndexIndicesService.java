package com.portfolio.service;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;

import com.portfolio.config.NSEIndicesConfig;
import com.portfolio.model.market.IndexIndices;
import com.portfolio.model.TimeInterval;
import com.portfolio.redis.service.MarketIndexIndicesRedisService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@RequiredArgsConstructor
@Slf4j
public class MarketIndexIndicesService {
    private final MarketIndexIndicesRedisService marketIndexIndicesRedisService;
    private final NSEIndicesConfig nseIndicesConfig;

    public List<IndexIndices> getAllMarketIndices(TimeInterval timeInterval, String type) {
        if (type != null && !type.isEmpty()) {
            type = type.toLowerCase();
            if (type.equals("broad")) {
                return getLatestNSEIndices(timeInterval, nseIndicesConfig.getBroadMarketIndices());
            } else if (type.equals("sector")) {
                return getLatestNSEIndices(timeInterval, nseIndicesConfig.getSectorIndices());
            } else {
                // Treat as specific index symbol
                return getLatestNSEIndices(timeInterval, List.of(type));
            }
        }
        return getLatestNSEIndices(timeInterval);
    }

    private List<IndexIndices> getLatestNSEIndices(TimeInterval timeInterval) {
        return getLatestNSEIndices(timeInterval, new ArrayList<>());
    }

    private List<IndexIndices> getLatestNSEIndices(TimeInterval timeInterval, List<String> indices) {
        if (indices.isEmpty()) {
            indices.addAll(nseIndicesConfig.getBroadMarketIndices());
            indices.addAll(nseIndicesConfig.getSectorIndices());
        }

        return indices.stream()
            .map(symbol -> marketIndexIndicesRedisService.getPrice(symbol, timeInterval))
            .filter(Optional::isPresent)
            .map(Optional::get)
            .sorted((i1, i2) -> {
                // Sort by market data (index value) in descending order
                int marketDataCompare = Double.compare(i2.getIndexIndices().getMarketData().getPercentChange(), i1.getIndexIndices().getMarketData().getPercentChange());
                if (marketDataCompare != 0) {
                    return marketDataCompare;
                }
                // If market data is equal, sort by percentage change in descending order
                return Double.compare(i2.getIndexIndices().getMarketData().getPercentChange(), i1.getIndexIndices().getMarketData().getPercentChange());
            })
            .collect(Collectors.toList());
    }
}