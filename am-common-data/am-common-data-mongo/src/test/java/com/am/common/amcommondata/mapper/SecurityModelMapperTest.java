package com.am.common.amcommondata.mapper;

import com.am.common.amcommondata.model.MarketCapType;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class SecurityModelMapperTest {

    private final SecurityModelMapper mapper = new SecurityModelMapper();

    @Test
    void getMarketCapType_nullOrNonPositive_returnsNull() {
        assertNull(mapper.getMarketCapType(null));
        assertNull(mapper.getMarketCapType(0.0));
        assertNull(mapper.getMarketCapType(-1.0));
    }

    @Test
    void getMarketCapType_bucketsFromAbsoluteInr() {
        assertEquals(MarketCapType.LARGE_CAP, mapper.getMarketCapType(1_000_000_000_000D));
        assertEquals(MarketCapType.MID_CAP, mapper.getMarketCapType(250_000_000_000D));
        assertEquals(MarketCapType.SMALL_CAP, mapper.getMarketCapType(50_000_000_000D));
        assertEquals(MarketCapType.MICRO_CAP, mapper.getMarketCapType(1_000_000_000D));
    }
}
