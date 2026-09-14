package com.portfolio.model.market;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class TimeFrameFromValueTest {

    @Test
    void acceptsJsonValues() {
        assertEquals(TimeFrame.DAY, TimeFrame.fromValue("1D"));
        assertEquals(TimeFrame.WEEK, TimeFrame.fromValue("1W"));
        assertEquals(TimeFrame.MONTH, TimeFrame.fromValue("1M"));
        assertEquals(TimeFrame.YEAR, TimeFrame.fromValue("1Y"));
    }

    @Test
    void acceptsLegacyAliasesCaseInsensitive() {
        assertEquals(TimeFrame.DAY, TimeFrame.fromValue("DAY"));
        assertEquals(TimeFrame.WEEK, TimeFrame.fromValue("week"));
        assertEquals(TimeFrame.MONTH, TimeFrame.fromValue("MONTH"));
        assertEquals(TimeFrame.YEAR, TimeFrame.fromValue("YEAR"));
        assertEquals(TimeFrame.WEEK, TimeFrame.fromValue("ONE_WEEK"));
    }

    @Test
    void unknownReturnsNull() {
        assertNull(TimeFrame.fromValue(null));
        assertNull(TimeFrame.fromValue(""));
        assertNull(TimeFrame.fromValue("bogus"));
    }
}
