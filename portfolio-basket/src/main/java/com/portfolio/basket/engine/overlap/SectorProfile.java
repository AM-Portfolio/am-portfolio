package com.portfolio.basket.engine.overlap;

import java.util.List;

public final class SectorProfile {
    public final boolean sectorial;
    public final String dominantSector;
    public final List<String> constituentIsins;

    public SectorProfile(boolean sectorial, String dominantSector, List<String> constituentIsins) {
        this.sectorial = sectorial;
        this.dominantSector = dominantSector;
        this.constituentIsins = constituentIsins;
    }
}
