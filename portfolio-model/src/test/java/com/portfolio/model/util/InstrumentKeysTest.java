package com.portfolio.model.util;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class InstrumentKeysTest {

    @Test
    void bareTicker_usesConfiguredCashPrefix() {
        assertEquals("NSE_EQ:PWL", InstrumentKeys.toInstrumentKey("PWL", "NSE_EQ"));
        assertEquals("CASH_EQ:PWL", InstrumentKeys.toInstrumentKey("PWL", "CASH_EQ"));
    }

    @Test
    void legacyNsePrefix_rewrittenToCash() {
        assertEquals("NSE_EQ:RELIANCE", InstrumentKeys.toInstrumentKey("NSE:RELIANCE", "NSE_EQ"));
    }

    @Test
    void foAndIndex_passthrough() {
        assertEquals("NSE_FO:NIFTY25APRFUT", InstrumentKeys.toInstrumentKey("NSE_FO:NIFTY25APRFUT", "NSE_EQ"));
        assertEquals("NSE_INDEX:NIFTY 50", InstrumentKeys.toInstrumentKey("NSE_INDEX:NIFTY 50", "NSE_EQ"));
    }

    @Test
    void alreadyCash_preserved() {
        assertEquals("NSE_EQ:IDEA", InstrumentKeys.toInstrumentKey("NSE_EQ:IDEA", "NSE_EQ"));
    }
}
