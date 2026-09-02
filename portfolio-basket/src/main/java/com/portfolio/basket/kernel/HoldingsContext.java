package com.portfolio.basket.kernel;

import com.portfolio.model.portfolio.EquityHoldings;
import com.portfolio.basket.util.SectorNormalizer;
import lombok.Getter;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Getter
public final class HoldingsContext {

    private final Map<String, EquityHoldings> userMap;
    private final Map<String, List<EquityHoldings>> userSectorMap;
    private final List<EquityHoldings> allUserHoldings;

    private HoldingsContext(
            Map<String, EquityHoldings> userMap,
            Map<String, List<EquityHoldings>> userSectorMap,
            List<EquityHoldings> allUserHoldings) {
        this.userMap = userMap;
        this.userSectorMap = userSectorMap;
        this.allUserHoldings = allUserHoldings;
    }

    public static HoldingsContext from(List<EquityHoldings> userHoldings) {
        List<EquityHoldings> holdings = userHoldings != null ? userHoldings : List.of();
        Map<String, EquityHoldings> userMap = holdings.stream()
                .collect(Collectors.toMap(EquityHoldings::getIsin, h -> h, (a, b) -> a));
        Map<String, List<EquityHoldings>> userSectorMap = holdings.stream()
                .filter(h -> h.getSector() != null && !SectorNormalizer.isUnknown(h.getSector()))
                .filter(h -> h.getAvailableQuantity() == null || h.getAvailableQuantity() > 0)
                .collect(Collectors.groupingBy(h -> SectorNormalizer.normalizeFine(h.getSector())));
        return new HoldingsContext(userMap, userSectorMap, holdings);
    }
}
