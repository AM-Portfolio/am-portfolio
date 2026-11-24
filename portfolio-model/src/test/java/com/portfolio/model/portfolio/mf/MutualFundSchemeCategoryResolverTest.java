package com.portfolio.model.portfolio.mf;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class MutualFundSchemeCategoryResolverTest {

    @Test
    void prefersStoredSchemeClass() {
        assertEquals(
                MutualFundSchemeCategory.LIQUID,
                MutualFundSchemeCategoryResolver.resolve("Liquid", "Something Random Fund"));
    }

    @Test
    void infersCommonSebiBucketsFromName() {
        assertEquals(
                MutualFundSchemeCategory.LARGE_CAP,
                MutualFundSchemeCategoryResolver.inferFromLabel("HDFC Large Cap Fund"));
        assertEquals(
                MutualFundSchemeCategory.SMALL_CAP,
                MutualFundSchemeCategoryResolver.inferFromLabel("Quant Small Cap Fund"));
        assertEquals(
                MutualFundSchemeCategory.ELSS,
                MutualFundSchemeCategoryResolver.inferFromLabel("HDFC ELSS Tax Saver"));
        assertEquals(
                MutualFundSchemeCategory.LIQUID,
                MutualFundSchemeCategoryResolver.inferFromLabel("ICICI Prudential Liquid Fund"));
        assertEquals(
                MutualFundSchemeCategory.OVERNIGHT,
                MutualFundSchemeCategoryResolver.inferFromLabel("UTI Overnight Fund"));
        assertEquals(
                MutualFundSchemeCategory.CONSERVATIVE_HYBRID,
                MutualFundSchemeCategoryResolver.inferFromLabel("HDFC Conservative Hybrid Fund"));
        assertEquals(
                MutualFundSchemeCategory.AGGRESSIVE_HYBRID,
                MutualFundSchemeCategoryResolver.inferFromLabel("HDFC Hybrid Equity Fund"));
        assertEquals(
                MutualFundSchemeCategory.INDEX,
                MutualFundSchemeCategoryResolver.inferFromLabel("Motilal Oswal Nifty 50 Index Fund"));
        assertEquals(
                MutualFundSchemeCategory.FOF_OVERSEAS,
                MutualFundSchemeCategoryResolver.inferFromLabel(
                        "Franklin U.S. Opportunities Equity Active Fund of Funds"));
        assertEquals(
                MutualFundSchemeCategory.SECTORAL,
                MutualFundSchemeCategoryResolver.inferFromLabel("HDFC Banking and Financial Services Fund"));
    }
}
