package com.portfolio.model.basket.cache;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * L2 Redis blob for basket catalog (themes + aliases).
 * {@code catalogVersion} bumps when classpath seed must overwrite stale Mongo.
 */
@Data
public class CachedBasketCatalog {
    /** Bump in basket-catalog.yml when seed content must replace existing Mongo. */
    private int catalogVersion = 1;
    private List<String> defaultThemeIds = new ArrayList<>();
    private List<Theme> themes = new ArrayList<>();

    @Data
    public static class Theme {
        private String id;
        private String label;
        private String query;
        private boolean featured = true;
        private List<String> indexAliases = new ArrayList<>();
    }
}
