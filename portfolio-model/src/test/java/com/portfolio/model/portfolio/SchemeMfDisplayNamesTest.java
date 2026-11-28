package com.portfolio.model.portfolio;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SchemeMfDisplayNamesTest {

    @Test
    void cleanBrokerDescription_quantCasString() {
        String raw = "QUANT MONEY MANAGERS LTD#QUANT MF-QUANT SMALL CAP FUND-DIRECT-GROWTH";
        String name = SchemeMfDisplayNames.resolveDisplayName(null, raw, "INF966L01689");
        assertTrue(name.toUpperCase().contains("QUANT SMALL CAP FUND"));
        assertFalse(name.startsWith("INF"));
    }

    @Test
    void cleanBrokerDescription_motilalCasString() {
        String raw = "MOTILAL OSWAL AMC LTD#MOTILAL OSWAL MF- MOTILAL OSWAL SMALL CAP FUND-DIRECT-GROWTH";
        String name = SchemeMfDisplayNames.resolveDisplayName(null, raw, "INF247L01BY3");
        assertTrue(name.toUpperCase().contains("MOTILAL OSWAL SMALL CAP"));
    }

    @Test
    void preferParserSchemeName() {
        assertEquals(
                "Quant Small Cap Fund - Direct Growth",
                SchemeMfDisplayNames.resolveDisplayName(
                        "Quant Small Cap Fund - Direct Growth",
                        "QUANT MONEY MANAGERS LTD#QUANT MF-QUANT SMALL CAP FUND-DIRECT-GROWTH",
                        "INF966L01689"));
    }

    @Test
    void displaySymbol_stripsPlanWords() {
        String symbol = SchemeMfDisplayNames.resolveDisplaySymbol(
                "QUANT SMALL CAP FUND DIRECT GROWTH", null, "INF966L01689");
        assertTrue(symbol.toUpperCase().contains("QUANT SMALL CAP"));
        assertFalse(symbol.toUpperCase().contains("DIRECT"));
    }

    @Test
    void displaySymbol_fromBrokerOnly_noCatalog() {
        String symbol = SchemeMfDisplayNames.resolveDisplaySymbol(
                null,
                "MOTILAL OSWAL AMC LTD#MOTILAL OSWAL MF- MOTILAL OSWAL LARGE CAP FUND-DIRECT-GROWTH",
                "INF247L01CE3");
        assertFalse(symbol.toUpperCase().startsWith("INF"));
        assertTrue(symbol.toUpperCase().contains("MOTILAL OSWAL LARGE CAP"));
    }

    @Test
    void fallsBackToIsinWhenNoLabel() {
        assertEquals(
                "INF247L01CE3",
                SchemeMfDisplayNames.resolveDisplaySymbol(null, null, "INF247L01CE3"));
    }
}
