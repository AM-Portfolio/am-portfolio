package com.portfolio.analytics.intelligence;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class IndexEtfSleevesTest {

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
    void broadNiftyEtfs_skipBankingShock() {
        assertThat(IndexEtfSleeves.isBroadNiftyEtf("NIFTYBEES")).isTrue();
        assertThat(IndexEtfSleeves.isBroadNiftyEtf("SETFNIF50")).isTrue();
        assertThat(IndexEtfSleeves.isBroadNiftyEtf("BANKBEES")).isFalse();
        assertThat(IndexEtfSleeves.isBroadNiftyEtf("ITBEES")).isFalse();
        assertThat(IndexEtfSleeves.isBroadNiftyEtf("GOLDBEES")).isFalse();
    }
}
