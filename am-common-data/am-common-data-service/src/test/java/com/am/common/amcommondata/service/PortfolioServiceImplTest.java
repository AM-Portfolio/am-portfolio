package com.am.common.amcommondata.service;

import com.am.common.amcommondata.document.portfolio.PortfolioDocument;
import com.am.common.amcommondata.mapper.PortfolioMapper;
import com.am.common.amcommondata.model.PortfolioModelV1;
import com.am.common.amcommondata.repository.portfolio.PortfolioDocumentRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PortfolioServiceImplTest {

    @Mock
    private PortfolioDocumentRepository portfolioDocumentRepository;

    @Mock
    private PortfolioMapper portfolioMapper;

    @InjectMocks
    private PortfolioServiceImpl portfolioService;

    @Test
    void getPortfoliosByUserId_skipsNullMappedNonUuidDocs() {
        String userId = "user-1";
        UUID goodId = UUID.fromString("11111111-1111-1111-1111-111111111111");

        PortfolioDocument goodDoc = PortfolioDocument.builder()
                .id(goodId.toString())
                .name("Good")
                .owner(userId)
                .build();
        PortfolioDocument sptDoc = PortfolioDocument.builder()
                .id("spt-portfolio-5-2")
                .name("SPT")
                .owner(userId)
                .build();

        PortfolioModelV1 goodModel = PortfolioModelV1.builder()
                .id(goodId)
                .name("Good")
                .owner(userId)
                .build();

        when(portfolioDocumentRepository.findByOwner(userId)).thenReturn(List.of(goodDoc, sptDoc));
        when(portfolioMapper.toModel(goodDoc)).thenReturn(goodModel);
        when(portfolioMapper.toModel(sptDoc)).thenReturn(null);

        List<PortfolioModelV1> result = portfolioService.getPortfoliosByUserId(userId);

        assertEquals(1, result.size());
        assertEquals(goodId, result.get(0).getId());
    }
}
