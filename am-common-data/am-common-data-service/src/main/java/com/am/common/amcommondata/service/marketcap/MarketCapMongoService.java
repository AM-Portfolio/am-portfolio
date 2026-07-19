package com.am.common.amcommondata.service.marketcap;

import com.am.common.amcommondata.document.marketcap.MarketCapDocument;
import com.am.common.amcommondata.repository.marketcap.MarketCapMongoRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class MarketCapMongoService {

    private final MarketCapMongoRepository repository;

    public Map<String, MarketCapDocument> getBySymbols(List<String> symbols) {
        if (symbols == null || symbols.isEmpty()) {
            return Collections.emptyMap();
        }
        try {
            List<MarketCapDocument> docs = repository.findBySymbolIn(symbols);
            return docs.stream().collect(Collectors.toMap(MarketCapDocument::getSymbol, d -> d));
        } catch (Exception e) {
            log.error("Error retrieving market cap from MongoDB for symbols: {}", symbols, e);
            return Collections.emptyMap();
        }
    }

    public void saveAll(Collection<MarketCapDocument> documents) {
        if (documents == null || documents.isEmpty()) {
            return;
        }
        try {
            repository.saveAll(documents);
        } catch (Exception e) {
            log.error("Error saving market cap to MongoDB", e);
        }
    }
}
