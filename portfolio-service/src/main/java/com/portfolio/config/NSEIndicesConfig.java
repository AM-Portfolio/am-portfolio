package com.portfolio.config;

import java.util.ArrayList;
import java.util.List;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConfigurationProperties(prefix = "nse")
public class NSEIndicesConfig {
    private List<String> broadMarketIndices;
    private List<String> sectorIndices;

    public NSEIndicesConfig() {
        this.broadMarketIndices = new ArrayList<>();
        this.sectorIndices = new ArrayList<>();
    }

    public List<String> getBroadMarketIndices() {
        return broadMarketIndices;
    }

    public void setBroadMarketIndices(List<String> broadMarketIndices) {
        this.broadMarketIndices = broadMarketIndices != null ? broadMarketIndices : new ArrayList<>();
    }

    public List<String> getSectorIndices() {
        return sectorIndices;
    }

    public void setSectorIndices(List<String> sectorIndices) {
        this.sectorIndices = sectorIndices != null ? sectorIndices : new ArrayList<>();
    }
}
