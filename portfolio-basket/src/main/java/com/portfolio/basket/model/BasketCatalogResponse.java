package com.portfolio.basket.model;

import lombok.Builder;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
@Builder
public class BasketCatalogResponse {
    private String defaultQuery;
    @Builder.Default
    private List<Theme> themes = new ArrayList<>();

    @Data
    @Builder
    public static class Theme {
        private String id;
        private String label;
        private String query;
        private boolean featured;
    }
}
