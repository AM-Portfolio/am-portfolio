package com.am.common.amcommondata.model.enums;

public enum TimeFrame {
    FIVE_MINUTES("5m", 5),
    FIFTEEN_MINUTES("15m", 15),
    ONE_HOUR("1h", 60),
    DAILY("1d", 1440),
    WEEKLY("1w", 10080),
    MONTHLY("1M", 43200),
    YEARLY("1Y", 525600);

    private final String code;
    private final int minutes;

    TimeFrame(String code, int minutes) {
        this.code = code;
        this.minutes = minutes;
    }

    public String getCode() {
        return code;
    }

    public int getMinutes() {
        return minutes;
    }
}
