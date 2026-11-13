package com.portfolio.model.reference;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class ListedEquitySectorCatalogTest {

    @Test
    void ideaResolvesToTelecommunicationBySymbolAndIsin() {
        ListedEquitySectorCatalog.Profile bySymbol = ListedEquitySectorCatalog.find("IDEA", null);
        ListedEquitySectorCatalog.Profile byIsin = ListedEquitySectorCatalog.find("IDEA", "INE669E01016");

        assertThat(bySymbol).isNotNull();
        assertThat(bySymbol.sector()).isEqualTo("Telecommunication");
        assertThat(bySymbol.industry()).contains("Telecom");
        assertThat(byIsin.sector()).isEqualTo("Telecommunication");
        assertThat(byIsin.isin()).isEqualTo("INE669E01016");
    }

    @Test
    void blankSectorIsNotACatalogHit() {
        assertThat(ListedEquitySectorCatalog.find("NOT-A-REAL-TICKER", null)).isNull();
    }

    @Test
    void jplResolvesToTelecommunicationBySymbolAndIsin() {
        ListedEquitySectorCatalog.Profile bySymbol = ListedEquitySectorCatalog.find("JPL", null);
        ListedEquitySectorCatalog.Profile byIsin = ListedEquitySectorCatalog.find("JPL", "INE0BJO01010");

        assertThat(bySymbol).isNotNull();
        assertThat(bySymbol.sector()).isEqualTo("Telecommunication");
        assertThat(bySymbol.industry()).contains("Telecom");
        assertThat(byIsin).isNotNull();
        assertThat(byIsin.sector()).isEqualTo("Telecommunication");
        assertThat(byIsin.isin()).isEqualTo("INE0BJO01010");
    }
}
