package com.portfolio.model.basket.cache;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * L2 Redis blob for basket catalog (themes + aliases).
 */
@Data
public class CachedBasketCatalog {
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
