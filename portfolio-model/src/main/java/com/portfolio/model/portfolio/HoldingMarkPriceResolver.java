package com.portfolio.model.portfolio;

/**
 * Single mark resolver for holdings, heatmap, and intelligence.
 *
 * <p>Listed equity / listed ETF → caller may use live LTP.
 * Quarantined scheme MF / unsafe AMC→equity maps → import mark / book residual only.
 */
public final class HoldingMarkPriceResolver {

    private HoldingMarkPriceResolver() {
    }

    public record Mark(double price, String priceSource) {
        public boolean isPresent() {
            return price > 0 && priceSource != null;
        }
    }

    /**
     * Resolve book mark when equity cash LTP must not be applied.
     * Order: importMark → residual currentValue/qty → statement currentPrice → cost basis.
     */
    public static Mark resolveQuarantinedMark(
            Double importMarkPrice,
            Double currentValue,
            Double quantity,
            Double currentPrice,
            Double avgBuyingPrice,
            Double investmentValue) {
        if (importMarkPrice != null && importMarkPrice > 0) {
            return new Mark(importMarkPrice, HoldingInstrumentClassifier.PRICE_IMPORT);
        }
        if (currentValue != null && currentValue > 0 && quantity != null && quantity > 0) {
            return new Mark(currentValue / quantity, HoldingInstrumentClassifier.PRICE_IMPORT);
        }
        if (currentPrice != null && currentPrice > 0) {
            return new Mark(currentPrice, HoldingInstrumentClassifier.PRICE_IMPORT);
        }
        if (avgBuyingPrice != null && avgBuyingPrice > 0) {
            return new Mark(avgBuyingPrice, HoldingInstrumentClassifier.PRICE_COST);
        }
        if (investmentValue != null && investmentValue > 0 && quantity != null && quantity > 0) {
            return new Mark(investmentValue / quantity, HoldingInstrumentClassifier.PRICE_COST);
        }
        return new Mark(0.0, null);
    }

    /**
     * Heatmap / analytics entry: if quarantined, return book mark; otherwise {@code null}
     * so the caller keeps the live LTP waterfall.
     */
    public static Mark tryQuarantineMark(
            String assetClass,
            String symbol,
            String isin,
            String name,
            Double importMarkPrice,
            Double currentValue,
            Double quantity,
            Double currentPrice,
            Double avgBuyingPrice,
            Double investmentValue) {
        if (!HoldingInstrumentClassifier.quarantineEquityLtp(assetClass, symbol, isin, name)) {
            return null;
        }
        return resolveQuarantinedMark(
                importMarkPrice, currentValue, quantity, currentPrice, avgBuyingPrice, investmentValue);
    }
}
