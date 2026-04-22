package com.am.common.amcommondata.model.enums;

public enum BrokerType {
    DHAN("Dhan"),
    ZERODHA("Zerodha"),
    MSTOCK("MStock"),
    GROW("Grow"),
    KOTAK("Kotak"),
    ANGEL_ONE("Angel One");

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

    public boolean isGrow() {
        return "Grow".equals(brokerName);
    }

    public boolean isKotak() {
        return "Kotak".equals(brokerName);
    }

    public boolean isAngelOne() {
        return "Angel One".equals(brokerName);
    }
}
