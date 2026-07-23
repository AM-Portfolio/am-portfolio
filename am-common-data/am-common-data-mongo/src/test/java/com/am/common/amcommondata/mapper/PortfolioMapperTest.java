package com.am.common.amcommondata.mapper;

import com.am.common.amcommondata.document.portfolio.PortfolioDocument;
import com.am.common.amcommondata.mapper.asset.EquityMapper;
import com.am.common.amcommondata.model.PortfolioModelV1;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

@ExtendWith(MockitoExtension.class)
class PortfolioMapperTest {

    @Mock
    private EquityMapper equityMapper;

    @InjectMocks
    private PortfolioMapper portfolioMapper;

    @Test
    void toModel_validUuid_mapsId() {
        UUID id = UUID.fromString("11111111-1111-1111-1111-111111111111");
        PortfolioDocument doc = PortfolioDocument.builder()
                .id(id.toString())
                .name("Real")
                .owner("user-1")
                .build();

        PortfolioModelV1 model = portfolioMapper.toModel(doc);

        assertNotNull(model);
        assertEquals(id, model.getId());
        assertEquals("Real", model.getName());
    }

    @Test
    void toModel_sptNonUuidId_returnsNull() {
        PortfolioDocument doc = PortfolioDocument.builder()
                .id("spt-portfolio-5-2")
                .name("SPT seed")
                .owner("user-1")
                .build();

        assertNull(portfolioMapper.toModel(doc));
    }

    @Test
    void toModel_blankId_returnsNull() {
        PortfolioDocument doc = PortfolioDocument.builder()
                .id("  ")
                .name("Blank")
                .build();

        assertNull(portfolioMapper.toModel(doc));
    }

    @Test
    void parseUuidOrNull_rejectsNonUuid() {
        assertNull(PortfolioMapper.parseUuidOrNull("spt-portfolio-5-2"));
        assertNotNull(PortfolioMapper.parseUuidOrNull("22222222-2222-2222-2222-222222222222"));
    }
}
