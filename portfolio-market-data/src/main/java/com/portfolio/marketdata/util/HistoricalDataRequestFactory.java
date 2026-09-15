package com.portfolio.marketdata.util;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Collection;
import java.util.stream.Collectors;

import com.portfolio.marketdata.model.FilterType;
import com.portfolio.marketdata.model.HistoricalDataRequest;
import com.portfolio.marketdata.model.InstrumentType;
import com.portfolio.model.analytics.request.TimeFrameRequest;
import com.portfolio.model.market.TimeFrame;

/**
 * Builds hist requests for period analytics. Analysis TF selects the date window;
 * candle interval is always daily ({@code 1D}).
 */
public final class HistoricalDataRequestFactory {

    private static final ZoneId MARKET_ZONE = ZoneId.of("Asia/Kolkata");

    private HistoricalDataRequestFactory() {
    }

    /**
     * Period start/end prices via daily candles + START_END filter.
     */
    public static HistoricalDataRequest forPeriodEndpoints(Collection<String> symbols, TimeFrameRequest tfr) {
        TimeFrameRequest resolved = resolveWindow(tfr);
        String symbolCsv = symbols == null
                ? ""
                : symbols.stream().filter(s -> s != null && !s.isEmpty()).collect(Collectors.joining(","));
        return HistoricalDataRequest.builder()
                .symbols(symbolCsv)
                .fromDate(resolved.getFromDate() != null ? resolved.getFromDate().toString() : null)
                .toDate(resolved.getToDate() != null ? resolved.getToDate().toString() : null)
                // Candle interval is always daily over the analysis window (not WEEK/MONTH/YEAR).
                .interval(TimeFrame.DAY.getValue())
                .filterType(FilterType.START_END.getValue())
                .instrumentType(InstrumentType.EQ.getValue())
                .continuous(false)
                .build();
    }

    /**
     * True when the resolved window is today→today (use live market data instead of hist).
     */
    public static boolean isSameDayLiveWindow(TimeFrameRequest tfr) {
        TimeFrameRequest resolved = resolveWindow(tfr);
        LocalDate today = LocalDate.now(MARKET_ZONE);
        return resolved.getFromDate() != null
                && resolved.getToDate() != null
                && resolved.getFromDate().isEqual(today)
                && resolved.getToDate().isEqual(today);
    }

    public static TimeFrameRequest resolveWindow(TimeFrameRequest tfr) {
        if (tfr == null || tfr.getTimeFrame() == null) {
            return tfr;
        }
        if (tfr.getFromDate() != null && tfr.getToDate() != null) {
            return tfr;
        }

        LocalDate today = LocalDate.now(MARKET_ZONE);
        LocalDate from;
        switch (tfr.getTimeFrame()) {
            case MINUTE:
            case THREE_MIN:
            case FIVE_MIN:
            case TEN_MIN:
            case FIFTEEN_MIN:
            case THIRTY_MIN:
            case HOUR:
            case FOUR_HOUR:
            case DAY:
                // Live / 1D analytics use today→today so SmartRoute skips hist and uses ticks.
                from = today;
                break;
            case WEEK:
                from = today.minusWeeks(1);
                break;
            case MONTH:
                from = today.minusMonths(1);
                break;
            case YEAR:
                from = today.minusYears(1);
                break;
            default:
                from = today.minusDays(7);
                break;
        }
        return TimeFrameRequest.builder()
                .fromDate(tfr.getFromDate() != null ? tfr.getFromDate() : from)
                .toDate(tfr.getToDate() != null ? tfr.getToDate() : today)
                .timeFrame(tfr.getTimeFrame())
                .build();
    }
}
