package com.portfolio.analytics.intelligence;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class SectorMatcherTest {

    @Test
    void it_doesNotMatchIndustrials() {
        assertThat(SectorMatcher.matches("Industrials", "IT")).isFalse();
        assertThat(SectorMatcher.matches("Industrials", "it")).isFalse();
    }

    @Test
    void it_matchesInformationTechnology() {
        assertThat(SectorMatcher.matches("Information Technology", "IT")).isTrue();
        assertThat(SectorMatcher.matches("Software", "IT")).isTrue();
        assertThat(SectorMatcher.matches("IT", "Information Technology")).isTrue();
    }

    @Test
    void banking_matchesFinancialServices() {
        assertThat(SectorMatcher.matches("Financial Services", "Banking")).isTrue();
    }

    @Test
    void unknown_neverMatches() {
        assertThat(SectorMatcher.matches("Unknown", "IT")).isFalse();
        assertThat(SectorMatcher.matches("Energy", "Unknown")).isFalse();
    }

    @Test
    void fmcg_matchesFastMovingConsumerGoods() {
        assertThat(SectorMatcher.matches("Fast Moving Consumer Goods", "FMCG")).isTrue();
        assertThat(SectorMatcher.matches("FMCG", "Fast Moving Consumer Goods")).isTrue();
        assertThat(SectorMatcher.matches("Consumer Staples", "FMCG")).isTrue();
        assertThat(SectorMatcher.matches("Information Technology", "FMCG")).isFalse();
    }
}
