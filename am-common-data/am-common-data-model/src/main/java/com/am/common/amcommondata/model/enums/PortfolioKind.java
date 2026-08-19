package com.am.common.amcommondata.model.enums;

/**
 * Distinguishes broker-synced portfolios from AM basket carve-outs.
 */
public enum PortfolioKind {
    BROKER,
    BASKET,
    DELETED;

    public static PortfolioKind orBroker(PortfolioKind kind) {
        return kind == null ? BROKER : kind;
    }

    public static boolean isBasket(PortfolioKind kind) {
        return kind == BASKET;
    }

    public static boolean isBroker(PortfolioKind kind) {
        return kind == null || kind == BROKER;
    }
}
