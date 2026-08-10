package com.portfolio.service.resolver;

import com.portfolio.model.resolver.TradingSymbolResolver;
import com.portfolio.model.util.SymbolResolver;
import lombok.extern.slf4j.Slf4j;
import org.bson.Document;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Service;

/**
 * Resolves ISIN → NSE/BSE trading symbol using {@code market_data.upstock_instruments}.
 *
 * <p>Fail-open: if Mongo is unavailable or the instrument is missing, returns the normalized
 * input so holdings are never dropped during save.
 */
@Service
@Slf4j
public class MongoTradingSymbolResolver implements TradingSymbolResolver {

    private static final String MARKET_DB = "market_data";
    private static final String INSTRUMENTS_COLLECTION = "upstock_instruments";

    private final MongoTemplate mongoTemplate;

    @Autowired
    public MongoTradingSymbolResolver(@Nullable MongoTemplate mongoTemplate) {
        this.mongoTemplate = mongoTemplate;
    }

    @Override
    public String resolveTradingSymbol(String symbol, String isin) {
        String normalizedSymbol = symbol != null ? SymbolResolver.normalize(symbol) : null;

        // Already a normal ticker — no DB lookup needed.
        if (normalizedSymbol != null && !normalizedSymbol.isBlank()
                && !TradingSymbolResolver.looksLikeIsin(normalizedSymbol)) {
            return normalizedSymbol.trim().toUpperCase();
        }

        String isinToResolve = pickIsin(normalizedSymbol, isin);
        if (isinToResolve == null) {
            return fallbackIdentifier(normalizedSymbol, isin);
        }

        String fromMongo = lookupTradingSymbolByIsin(isinToResolve);
        if (fromMongo != null) {
            return fromMongo;
        }

        return fallbackIdentifier(normalizedSymbol, isin);
    }

    private String pickIsin(String normalizedSymbol, String isin) {
        if (isin != null && !isin.isBlank()) {
            return isin.trim().toUpperCase();
        }
        if (TradingSymbolResolver.looksLikeIsin(normalizedSymbol)) {
            return normalizedSymbol.trim().toUpperCase();
        }
        return null;
    }

    private String lookupTradingSymbolByIsin(String isin) {
        if (mongoTemplate == null) {
            log.debug("MongoTemplate not available — skipping ISIN lookup for {}", isin);
            return null;
        }
        try {
            Document inst = mongoTemplate.getMongoDatabaseFactory()
                    .getMongoDatabase(MARKET_DB)
                    .getCollection(INSTRUMENTS_COLLECTION)
                    .find(new Document("isin", isin))
                    .first();
            if (inst != null && inst.getString("trading_symbol") != null
                    && !inst.getString("trading_symbol").isBlank()) {
                return inst.getString("trading_symbol").trim().toUpperCase();
            }
        } catch (Exception ex) {
            // Fail-open: save must continue even if instrument master is down.
            log.warn("ISIN lookup failed for {}: {}", isin, ex.getMessage());
        }
        return null;
    }

    private String fallbackIdentifier(String normalizedSymbol, String isin) {
        if (normalizedSymbol != null && !normalizedSymbol.isBlank()) {
            return normalizedSymbol.trim().toUpperCase();
        }
        if (isin != null && !isin.isBlank()) {
            return isin.trim().toUpperCase();
        }
        return null;
    }
}
