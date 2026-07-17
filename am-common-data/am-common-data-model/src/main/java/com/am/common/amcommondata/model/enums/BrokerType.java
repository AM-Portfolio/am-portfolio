package com.am.common.amcommondata.model.enums;

public enum BrokerType {
    DHAN("Dhan"),
    ZERODHA("Zerodha"),
    MSTOCK("MStock"),
    GROWW("Groww"),
    KOTAK("Kotak"),
    ANGEL_ONE("Angel One"),
    UPSTOX("Upstox");

    private String brokerName;

    BrokerType(String brokerName) {
        this.brokerName = brokerName;
    }

    public static BrokerType fromCode(String code) {
        for (BrokerType type : values()) {
            if (type.getCode().equals(code)) {
                return type;
            }
        }
        return null;
    }

    public String getCode() {
        return brokerName;
    }

    public boolean isDhan() {
        return "Dhan".equals(brokerName);
    }

    public boolean isZerodha() {
        return "Zerodha".equals(brokerName);
    }

    public boolean isMStock() {
        return "MStock".equals(brokerName);
    }

    public boolean isGroww() {
        return "Groww".equals(brokerName);
    }

    public boolean isKotak() {
        return "Kotak".equals(brokerName);
    }

    public boolean isAngelOne() {
        return "Angel One".equals(brokerName);
    }

    public boolean isUpstox() {
        return "Upstox".equals(brokerName);
    }
}
