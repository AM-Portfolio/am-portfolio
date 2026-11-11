package com.am.common.amcommondata.service;

import com.am.common.amcommondata.model.PortfolioModelV1;
import com.am.common.amcommondata.model.enums.BrokerType;
import com.am.common.amcommondata.model.enums.PortfolioKind;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PortfolioServiceImplCollapseTest {

    @Test
    void collapseKeepsPrimaryAndFamilyGrowwSeparate() {
        PortfolioModelV1 you = book("Groww", BrokerType.GROWW, null, 1830.75);
        PortfolioModelV1 family = book(
                "Groww", BrokerType.GROWW, "50473477-cf90-4293-87eb-18c959b242e3", 106711.35);

        List<PortfolioModelV1> out =
                PortfolioServiceImpl.collapseBrokerModelsForDisplay(List.of(you, family));

        assertEquals(2, out.size());
        assertTrue(out.stream().anyMatch(p -> p.getFamilyMemberId() == null));
        assertTrue(out.stream().anyMatch(p -> "50473477-cf90-4293-87eb-18c959b242e3"
                .equals(p.getFamilyMemberId())));
    }

    @Test
    void withFamilyScopeDistinguishesYouFromMember() {
        assertEquals(
                "broker:GROWW|fm:you",
                PortfolioServiceImpl.withFamilyScope("broker:GROWW", null));
        assertEquals(
                "broker:GROWW|fm:fm-1",
                PortfolioServiceImpl.withFamilyScope("broker:GROWW", "fm-1"));
    }

    private static PortfolioModelV1 book(
            String name, BrokerType broker, String familyMemberId, double value) {
        PortfolioModelV1 p = new PortfolioModelV1();
        p.setId(UUID.randomUUID());
        p.setName(name);
        p.setBrokerType(broker);
        p.setPortfolioKind(PortfolioKind.BROKER);
        p.setFamilyMemberId(familyMemberId);
        p.setTotalValue(value);
        return p;
    }
}
