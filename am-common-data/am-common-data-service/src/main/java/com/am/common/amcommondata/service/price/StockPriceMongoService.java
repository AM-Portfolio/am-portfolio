package com.am.common.amcommondata.service.price;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;

import com.am.common.amcommondata.document.price.StockPriceDocument;
import com.am.common.amcommondata.repository.price.StockPriceMongoRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@RequiredArgsConstructor
@Slf4j
public class StockPriceMongoService {

    private final StockPriceMongoRepository stockPriceMongoRepository;

    public void saveAll(List<StockPriceDocument> prices) {
        if (prices == null || prices.isEmpty()) {
            return;
        }
        log.debug("Saving {} stock prices to MongoDB", prices.size());
        try {
            stockPriceMongoRepository.saveAll(prices);
        } catch (Exception e) {
            log.error("Failed to save stock prices to MongoDB", e);
        }
    }

    public Map<String, StockPriceDocument> getPrices(List<String> symbols) {
        if (symbols == null || symbols.isEmpty()) {
            return Map.of();
        }
        return stockPriceMongoRepository.findBySymbolIn(symbols)
                .stream()
                .collect(Collectors.toMap(StockPriceDocument::getSymbol, doc -> doc));
    }
}
