package com.portfolio.mapper.holdings;

import com.am.common.amcommondata.model.PortfolioModelV1;
import com.am.common.amcommondata.model.asset.equity.EquityModel;
import com.am.common.amcommondata.model.enums.PortfolioKind;
import com.portfolio.model.portfolio.EquityHoldings;
import com.portfolio.service.basket.AllocationLedgerService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PortfolioHoldingsMapperIsinMergeTest {

    @Mock
    private AllocationLedgerService allocationLedgerService;

    @Test
    void distinctIsinsWithSamePoisonedSymbolAreNotSummed() {
        when(allocationLedgerService.getActiveAllocationsMap(anyString())).thenReturn(Map.of());

        PortfolioHoldingsMapper mapper = new PortfolioHoldingsMapper(allocationLedgerService);

        EquityModel bandhan = EquityModel.builder()
                .symbol("RECLTD")
                .isin("INF194KB1AL4")
                .name("BANDHAN AMC LTD#BANDHAN MF-BANDHAN SMALL CAP FUND-DIRECT-GROWTH")
                .assetClass("mutual_fund_demat")
                .quantity(15_542.681)
                .importMarkPrice(53.227)
                .currentValue(827_290.28)
                .build();
        EquityModel motilal = EquityModel.builder()
                .symbol("RECLTD")
                .isin("INF247L01CE3")
                .name("MOTILAL OSWAL AMC LTD#MOTILAL OSWAL MF- MOTILAL OSWAL LARGE CAP FUND-DIRECT-GROWTH")
                .assetClass("mutual_fund_demat")
                .quantity(85_208.657)
                .importMarkPrice(13.593)
                .currentValue(1_158_241.27)
                .build();

        PortfolioModelV1 book = PortfolioModelV1.builder()
                .id(UUID.randomUUID())
                .name("Zerodha")
                .portfolioKind(PortfolioKind.BROKER)
                .equityModels(List.of(bandhan, motilal))
                .build();

        List<EquityHoldings> rows = mapper.toEquityHoldings(List.of(book));
        assertEquals(2, rows.size());
        double bandhanQty = rows.stream()
                .filter(r -> "INF194KB1AL4".equalsIgnoreCase(r.getIsin()))
                .mapToDouble(EquityHoldings::getQuantity)
                .findFirst()
                .orElse(-1);
        double motilalQty = rows.stream()
                .filter(r -> "INF247L01CE3".equalsIgnoreCase(r.getIsin()))
                .mapToDouble(EquityHoldings::getQuantity)
                .findFirst()
                .orElse(-1);
        assertEquals(15_542.681, bandhanQty, 1e-6);
        assertEquals(85_208.657, motilalQty, 1e-6);
    }
}
