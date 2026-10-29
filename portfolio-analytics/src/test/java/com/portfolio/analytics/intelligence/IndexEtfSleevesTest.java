package com.portfolio.analytics.intelligence;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class IndexEtfSleevesTest {

    @BeforeEach
    void seedFromYaml() {
        StressProperties props = StressProperties.loadClasspathDefaults();
        IndexEtfSleeves.replaceRawIndex(props.getRawIndices());
        IndexEtfSleeves.replaceBroadNiftyEtf(props.getBroadNiftyEtfs());
    }

    @AfterEach
    void tearDown() {
        IndexEtfSleeves.resetConfiguredForTesting();
    }

    @Test
    void rawIndexAliases_excludeFromBetaQuantities() {
        assertThat(IndexEtfSleeves.isRawIndexAlias("NIFTY 50")).isTrue();
        assertThat(IndexEtfSleeves.isRawIndexAlias("NIFTY50")).isTrue();
        assertThat(IndexEtfSleeves.isRawIndexAlias("SENSEX")).isTrue();
        assertThat(IndexEtfSleeves.isRawIndexAlias("BANKNIFTY")).isTrue();
        assertThat(IndexEtfSleeves.isRawIndexAlias("NIFTYBEES")).isFalse();
        assertThat(IndexEtfSleeves.isRawIndexAlias("RELIANCE")).isFalse();
    }

    @Test
    void withoutRegistration_rawListsEmpty_heuristicsStillApply() {
        IndexEtfSleeves.resetConfiguredForTesting();
        // Exact list membership requires seed/Mongo; bare SENSEX is not a heuristic hit
        assertThat(IndexEtfSleeves.isRawIndexAlias("SENSEX")).isFalse();
        // Heuristics still work without seed
        assertThat(IndexEtfSleeves.isRawIndexAlias("NIFTY_TRI")).isTrue();
        assertThat(IndexEtfSleeves.isBroadNiftyEtf("NIFTYBEES")).isTrue();
    }

    @Test
    void broadNiftyEtfs_skipBankingShock() {
        assertThat(IndexEtfSleeves.isBroadNiftyEtf("NIFTYBEES")).isTrue();
        assertThat(IndexEtfSleeves.isBroadNiftyEtf("SETFNIF50")).isTrue();
        assertThat(IndexEtfSleeves.isBroadNiftyEtf("BANKBEES")).isFalse();
        assertThat(IndexEtfSleeves.isBroadNiftyEtf("ITBEES")).isFalse();
        assertThat(IndexEtfSleeves.isBroadNiftyEtf("GOLDBEES")).isFalse();
    }

    @Test
    void registerRawIndex_dynamicallyExtendsRawIndexAliases() {
        assertThat(IndexEtfSleeves.isRawIndexAlias("CUSTOM_IND")).isFalse();
        IndexEtfSleeves.registerRawIndex(List.of("CUSTOM_IND"));
        assertThat(IndexEtfSleeves.isRawIndexAlias("CUSTOM_IND")).isTrue();
        assertThat(IndexEtfSleeves.isRawIndexAlias("custom-ind")).isTrue();
    }

    @Test
    void registerBroadNiftyEtf_dynamicallyExtendsBroadEtfs() {
        assertThat(IndexEtfSleeves.isBroadNiftyEtf("CUSTOM_ETF")).isFalse();
        IndexEtfSleeves.registerBroadNiftyEtf(List.of("CUSTOM_ETF"));
        assertThat(IndexEtfSleeves.isBroadNiftyEtf("CUSTOM_ETF")).isTrue();
    }

    @Test
    void indexPatterns_recognizedAsRawIndexAlias() {
        assertThat(IndexEtfSleeves.isRawIndexAlias("NIFTY_TRI")).isTrue();
        assertThat(IndexEtfSleeves.isRawIndexAlias("NSE_INDEX")).isTrue();
    }
}
