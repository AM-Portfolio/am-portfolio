package com.am.common.amcommondata.model.enums;

public enum BrokerType {
    DHAN("Dhan"),
    ZERODHA("Zerodha"),
    MSTOCK("MStock"),
    GROWW("Groww"),
    GROW("Groww"), // Fallback for existing db records
    KOTAK("Kotak"),
    ANGEL_ONE("Angel One"),
    UPSTOX("Upstox"),
    FYERS("Fyers"),
    HDFC("HDFC"),
    CDSL("CDSL"),
    NPS("NPS");

    private final String brokerName;

    BrokerType(String brokerName) {
        this.brokerName = brokerName;
    }

    public static BrokerType fromCode(String code) {
        if (code == null || code.isBlank()) {
            return null;
        }
        String trimmed = code.trim();
        for (BrokerType type : values()) {
            if (type.getCode().equalsIgnoreCase(trimmed)
                    || type.name().equalsIgnoreCase(trimmed)) {
                return type;
            }
        }
        String u = trimmed.toUpperCase().replace('-', '_').replace(' ', '_');
        if ("GROW".equals(u) || "GROWW".equals(u)) {
            return GROWW;
        }
        if ("ANGLEONE".equals(u) || "ANGELONE".equals(u) || "ANGEL_ONE".equals(u)) {
            return ANGEL_ONE;
        }
        if ("HDFC_BANK".equals(u) || "HDFCSECURITIES".equals(u)) {
            return HDFC;
        }
        try {
            return BrokerType.valueOf(u);
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }

    public String getCode() {
        return brokerName;
    }

    public boolean isDhan() {
        return this == DHAN;
    }

    public boolean isZerodha() {
        return this == ZERODHA;
    }

    public boolean isMStock() {
        return this == MSTOCK;
    }

    public boolean isGroww() {
        return this == GROW || this == GROWW;
    }

    public boolean isKotak() {
        return this == KOTAK;
    }

    public boolean isAngelOne() {
        return this == ANGEL_ONE;
    }

    public boolean isUpstox() {
        return this == UPSTOX;
    }

    public boolean isCdsl() {
        return this == CDSL;
    }

    public boolean isNps() {
        return this == NPS;
    }
}
