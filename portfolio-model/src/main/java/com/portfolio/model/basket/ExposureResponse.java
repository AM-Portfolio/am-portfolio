package com.portfolio.model.basket;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.annotation.JsonInclude;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ExposureResponse {
    private String userId;
    private String portfolioId;

    private List<StockExposure> stockExposure;
    private List<SectorExposure> sectorExposure;

    @Data
    @Builder
    @AllArgsConstructor
    @NoArgsConstructor
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class StockExposure {
        private String isin;
        private String symbol;
        private String sector;
        private double totalWeight; // Cumulative %
        private double directWeight; // Direct stock %
        private double indirectWeight; // Exposure via ETFs
        private List<EtfExposureSource> sources; // Which ETFs contribute to this?
    }

    @Data
    @Builder
    @AllArgsConstructor
    @NoArgsConstructor
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class SectorExposure {
        private String sector;
        private double weight;
    }

    /**
     * Represents a source of stock exposure, either from an ETF holding or direct
     * portfolio holding.
     * For ETF sources: etfIsin and etfSymbol will be populated
     * For direct portfolio holdings: portfolioId and portfolioName will be
     * populated
     */
    @Data
    @Builder
    @AllArgsConstructor
    @NoArgsConstructor
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class EtfExposureSource {
        private String etfIsin; // ISIN of the ETF (null for direct holdings)
        private String etfSymbol; // Symbol of the ETF (null for direct holdings)
        private String portfolioId; // Portfolio UUID (null for ETF holdings)
        private String portfolioName; // Portfolio name (null for ETF holdings)
        private double contribution; // Weight * ETF_Allocation or direct weight
    }
}
