package com.am.common.amcommondata.model.enums;

/**
 * Distinguishes broker-synced books, baskets, paper trading, and per-user demo clones.
 */
public enum PortfolioKind {
    BROKER,
    BASKET,
    DELETED,
    /** Per-user sandbox cloned from the configured demo template. */
    DEMO,
    /** Paper trading desk book (must not hide Demo eligibility). */
    PAPER;

    public static PortfolioKind orBroker(PortfolioKind kind) {
        return kind == null ? BROKER : kind;
    }

    public static boolean isBasket(PortfolioKind kind) {
        return kind == BASKET;
    }

    /** Legacy null and BROKER only — not DEMO/PAPER/BASKET/DELETED. */
    public static boolean isBroker(PortfolioKind kind) {
        return kind == null || kind == BROKER;
    }

    public static boolean isDemo(PortfolioKind kind) {
        return kind == DEMO;
    }

    public static boolean isPaper(PortfolioKind kind) {
        return kind == PAPER;
    }

    public static boolean isDeleted(PortfolioKind kind) {
        return kind == DELETED;
    }

    /**
     * Real broker book that should hide Demo: BROKER/null with a concrete {@link BrokerType}.
     * Paper, baskets, demos, CDSL (CAS aggregator), and broker-kind rows without brokerType
     * do not qualify — CDSL alone must not soft-hide Demo or empty the portfolio list.
     */
    public static boolean isRealBrokerBook(PortfolioKind kind, BrokerType brokerType) {
        if (!isBroker(kind)) {
            return false;
        }
        if (brokerType == null || brokerType.isCdsl()) {
            return false;
        }
        return true;
    }
}
