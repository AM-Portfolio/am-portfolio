package com.portfolio.analytics.service.utils;

import com.am.common.amcommondata.model.MarketCapType;
import com.am.common.amcommondata.model.security.SecurityKeyModel;
import com.am.common.amcommondata.model.security.SecurityMetadataModel;
import com.am.common.amcommondata.model.security.SecurityModel;
import com.am.common.amcommondata.service.SecurityService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SecurityDetailsServiceTest {

    @Mock private SecurityService securityService;
    @InjectMocks private SecurityDetailsService service;

    private SecurityModel buildSecurity(String symbol, String sector, String industry, MarketCapType cap) {
        SecurityKeyModel key = mock(SecurityKeyModel.class);
        lenient().when(key.getSymbol()).thenReturn(symbol);
        SecurityMetadataModel meta = mock(SecurityMetadataModel.class);
        lenient().when(meta.getSector()).thenReturn(sector);
        lenient().when(meta.getIndustry()).thenReturn(industry);
        lenient().when(meta.getMarketCapType()).thenReturn(cap);
        SecurityModel model = mock(SecurityModel.class);
        lenient().when(model.getKey()).thenReturn(key);
        lenient().when(model.getMetadata()).thenReturn(meta);
        return model;
    }

    @Test void getSecurityDetails_nullSymbols() {
        assertTrue(service.getSecurityDetails(null).isEmpty());
    }

    @Test void getSecurityDetails_emptySymbols() {
        assertTrue(service.getSecurityDetails(List.of()).isEmpty());
    }

    @Test void getSecurityDetails_success() {
        var sec = buildSecurity("AAPL", "Tech", "Software", MarketCapType.LARGE_CAP);
        when(securityService.findBySymbols(List.of("AAPL"))).thenReturn(List.of(sec));
        var result = service.getSecurityDetails(List.of("AAPL"));
        assertEquals(1, result.size());
        assertNotNull(result.get("AAPL"));
    }

    @Test void getSecurityDetails_partialResults_logsMissing() {
        var sec = buildSecurity("AAPL", "Tech", "Software", MarketCapType.LARGE_CAP);
        when(securityService.findBySymbols(List.of("AAPL", "MISS"))).thenReturn(List.of(sec));
        var result = service.getSecurityDetails(List.of("AAPL", "MISS"));
        assertEquals(1, result.size());
    }

    @Test void getSecurityDetails_bulkFailure_fallbackOneByOne() {
        when(securityService.findBySymbols(List.of("A", "B")))
                .thenThrow(new RuntimeException("bulk fail"));
        var secA = buildSecurity("A", "Tech", "Soft", null);
        when(securityService.findBySymbols(List.of("A"))).thenReturn(List.of(secA));
        when(securityService.findBySymbols(List.of("B"))).thenReturn(List.of());

        var result = service.getSecurityDetails(List.of("A", "B"));
        assertEquals(1, result.size());
        assertNotNull(result.get("A"));
    }

    @Test void getSymbolMapSectors_success() {
        var sec = buildSecurity("AAPL", "Technology", "Software", null);
        when(securityService.findBySymbols(List.of("AAPL"))).thenReturn(List.of(sec));
        var result = service.getSymbolMapSectors(List.of("AAPL"));
        assertEquals("Technology", result.get("AAPL"));
    }

    @Test void getSymbolMapSectors_nullSector_returnsUnknown() {
        var sec = buildSecurity("AAPL", null, null, null);
        when(securityService.findBySymbols(List.of("AAPL"))).thenReturn(List.of(sec));
        var result = service.getSymbolMapSectors(List.of("AAPL"));
        assertEquals("Unknown", result.get("AAPL"));
    }

    @Test void groupSymbolsBySector_nullSymbols() {
        assertTrue(service.groupSymbolsBySector(null).isEmpty());
    }

    @Test void groupSymbolsBySector_emptySymbols() {
        assertTrue(service.groupSymbolsBySector(List.of()).isEmpty());
    }

    @Test void groupSymbolsBySector_success() {
        var s1 = buildSecurity("A", "Tech", null, null);
        var s2 = buildSecurity("B", "Tech", null, null);
        var s3 = buildSecurity("C", "Finance", null, null);
        when(securityService.findBySymbols(List.of("A", "B", "C"))).thenReturn(List.of(s1, s2, s3));
        var result = service.groupSymbolsBySector(List.of("A", "B", "C"));
        assertEquals(2, result.size());
        assertEquals(2, result.get("Tech").size());
    }

    @Test void groupSymbolsByIndustry_nullSymbols() {
        assertTrue(service.groupSymbolsByIndustry(null).isEmpty());
    }

    @Test void groupSymbolsByIndustry_success() {
        var s1 = buildSecurity("A", "Tech", "Software", null);
        var s2 = buildSecurity("B", "Tech", "Hardware", null);
        when(securityService.findBySymbols(List.of("A", "B"))).thenReturn(List.of(s1, s2));
        var result = service.groupSymbolsByIndustry(List.of("A", "B"));
        assertEquals(2, result.size());
    }

    @Test void groupSymbolsByIndustry_nullIndustry_usesUnknown() {
        var sec = buildSecurity("A", "Tech", null, null);
        when(securityService.findBySymbols(List.of("A"))).thenReturn(List.of(sec));
        var result = service.groupSymbolsByIndustry(List.of("A"));
        assertTrue(result.containsKey("Unknown"));
    }

    @Test void groupSymbolsByMarketType_nullSymbols() {
        assertTrue(service.groupSymbolsByMarketType(null).isEmpty());
    }

    @Test void groupSymbolsByMarketType_success() {
        var s1 = buildSecurity("A", null, null, MarketCapType.LARGE_CAP);
        var s2 = buildSecurity("B", null, null, MarketCapType.SMALL_CAP);
        when(securityService.findBySymbols(List.of("A", "B"))).thenReturn(List.of(s1, s2));
        var result = service.groupSymbolsByMarketType(List.of("A", "B"));
        assertEquals(2, result.size());
    }

    @Test void groupSymbolsByMarketType_nullCapType_usesUnknown() {
        var sec = buildSecurity("A", null, null, null);
        when(securityService.findBySymbols(List.of("A"))).thenReturn(List.of(sec));
        var result = service.groupSymbolsByMarketType(List.of("A"));
        assertTrue(result.containsKey("UNKNOWN"));
    }
}
