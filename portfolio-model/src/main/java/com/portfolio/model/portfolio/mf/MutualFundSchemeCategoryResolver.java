package com.portfolio.model.portfolio.mf;

import java.util.Locale;

/**
 * Resolves SEBI-style MF scheme category from stored {@code schemeClass} or scheme name.
 */
public final class MutualFundSchemeCategoryResolver {

    private MutualFundSchemeCategoryResolver() {}

    /**
     * @param storedSchemeClass optional persisted category (EquityModel.schemeClass / seed)
     * @param schemeName        scheme / company name
     */
    public static MutualFundSchemeCategory resolve(String storedSchemeClass, String schemeName) {
        return MutualFundSchemeCategory.fromDisplayName(storedSchemeClass)
                .filter(c -> c != MutualFundSchemeCategory.UNKNOWN)
                .orElseGet(() -> inferFromLabel(schemeName));
    }

    public static String resolveDisplayName(String storedSchemeClass, String schemeName) {
        return resolve(storedSchemeClass, schemeName).getDisplayName();
    }

    public static MutualFundSchemeCategory inferFromLabel(String label) {
        if (label == null || label.isBlank()) {
            return MutualFundSchemeCategory.UNKNOWN;
        }
        String s = label.toLowerCase(Locale.ROOT);

        // FoF / overseas first (Franklin U.S. Opportunities Equity FoF)
        if (s.contains("fund of fund") || s.contains("fund of funds") || s.contains(" fof")
                || s.contains("feeder")) {
            if (s.contains("overseas") || s.contains("u.s") || s.contains("us ")
                    || s.contains("global") || s.contains("international") || s.contains("nasdaq")
                    || s.contains("s&p")) {
                return MutualFundSchemeCategory.FOF_OVERSEAS;
            }
            return MutualFundSchemeCategory.FOF_DOMESTIC;
        }

        // Debt (before equity keywords like "fund")
        if (s.contains("overnight")) {
            return MutualFundSchemeCategory.OVERNIGHT;
        }
        if (s.contains("liquid")) {
            return MutualFundSchemeCategory.LIQUID;
        }
        if (s.contains("money market")) {
            return MutualFundSchemeCategory.MONEY_MARKET;
        }
        if (s.contains("ultra short")) {
            return MutualFundSchemeCategory.ULTRA_SHORT_DURATION;
        }
        if (s.contains("low duration")) {
            return MutualFundSchemeCategory.LOW_DURATION;
        }
        if (s.contains("short duration") || s.contains("short term debt") || s.contains("short term fund")) {
            return MutualFundSchemeCategory.SHORT_DURATION;
        }
        if (s.contains("medium to long") || s.contains("medium long")) {
            return MutualFundSchemeCategory.MEDIUM_TO_LONG_DURATION;
        }
        if (s.contains("medium duration") || s.contains("medium term debt")) {
            return MutualFundSchemeCategory.MEDIUM_DURATION;
        }
        if (s.contains("long duration") || s.contains("long term debt")) {
            return MutualFundSchemeCategory.LONG_DURATION;
        }
        if (s.contains("dynamic bond")) {
            return MutualFundSchemeCategory.DYNAMIC_BOND;
        }
        if (s.contains("corporate bond")) {
            return MutualFundSchemeCategory.CORPORATE_BOND;
        }
        if (s.contains("credit risk")) {
            return MutualFundSchemeCategory.CREDIT_RISK;
        }
        if (s.contains("banking and psu") || s.contains("banking & psu") || s.contains("psu debt")) {
            return MutualFundSchemeCategory.BANKING_AND_PSU;
        }
        if (s.contains("gilt")) {
            return MutualFundSchemeCategory.GILT;
        }
        if (s.contains("floater") || s.contains("floating rate")) {
            return MutualFundSchemeCategory.FLOATER;
        }

        // Hybrid
        if (s.contains("conservative hybrid")) {
            return MutualFundSchemeCategory.CONSERVATIVE_HYBRID;
        }
        if (s.contains("aggressive hybrid")) {
            return MutualFundSchemeCategory.AGGRESSIVE_HYBRID;
        }
        if (s.contains("balanced hybrid") || (s.contains("balanced") && s.contains("hybrid"))) {
            return MutualFundSchemeCategory.BALANCED_HYBRID;
        }
        if (s.contains("balanced advantage") || s.contains("dynamic asset allocation")
                || s.contains("dynamic asset")) {
            return MutualFundSchemeCategory.DYNAMIC_ASSET_ALLOCATION;
        }
        if (s.contains("multi asset")) {
            return MutualFundSchemeCategory.MULTI_ASSET_ALLOCATION;
        }
        if (s.contains("arbitrage")) {
            return MutualFundSchemeCategory.ARBITRAGE;
        }
        if (s.contains("equity savings")) {
            return MutualFundSchemeCategory.EQUITY_SAVINGS;
        }
        if (s.contains("hybrid")) {
            return MutualFundSchemeCategory.AGGRESSIVE_HYBRID;
        }

        // Equity style
        if (s.contains("elss") || s.contains("tax saver") || s.contains("tax-saver")) {
            return MutualFundSchemeCategory.ELSS;
        }
        if (s.contains("index")
                || s.contains("sensex")
                || (s.contains("nifty") && (s.contains("momentum") || s.contains("etf")))) {
            return MutualFundSchemeCategory.INDEX;
        }
        if (s.contains("sectoral")
                || s.contains("banking and financial")
                || s.contains("banking fund")
                || s.contains("pharma")
                || s.contains("infrastructure fund")
                || s.contains("technology fund")
                || s.contains("it fund")
                || s.contains("fmcg fund")
                || s.contains("energy fund")) {
            return MutualFundSchemeCategory.SECTORAL;
        }
        if (s.contains("thematic") || s.contains("esg") || s.contains("consumption fund")
                || s.contains("manufacturing") || s.contains("digital india")) {
            return MutualFundSchemeCategory.THEMATIC;
        }
        if (s.contains("dividend yield")) {
            return MutualFundSchemeCategory.DIVIDEND_YIELD;
        }
        if (s.contains("contra")) {
            return MutualFundSchemeCategory.CONTRA;
        }
        if (s.contains("value fund") || s.contains(" value ")) {
            return MutualFundSchemeCategory.VALUE;
        }
        if (s.contains("focused")) {
            return MutualFundSchemeCategory.FOCUSED;
        }
        if (s.contains("large & mid") || s.contains("large and mid") || s.contains("large mid")) {
            return MutualFundSchemeCategory.LARGE_AND_MID_CAP;
        }
        if (s.contains("small cap") || s.contains("smallcap")) {
            return MutualFundSchemeCategory.SMALL_CAP;
        }
        if (s.contains("mid cap") || s.contains("midcap") || s.contains("emerging equity")) {
            return MutualFundSchemeCategory.MID_CAP;
        }
        if (s.contains("flexi")) {
            return MutualFundSchemeCategory.FLEXI_CAP;
        }
        if (s.contains("multi cap") || s.contains("multicap")) {
            return MutualFundSchemeCategory.MULTI_CAP;
        }
        if (s.contains("large cap") || s.contains("largecap") || s.contains("bluechip")
                || s.contains("blue chip")) {
            return MutualFundSchemeCategory.LARGE_CAP;
        }

        if (s.contains("bond") || s.contains("debt") || s.contains("income fund")) {
            return MutualFundSchemeCategory.DYNAMIC_BOND;
        }

        return MutualFundSchemeCategory.UNKNOWN;
    }
}
