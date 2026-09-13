package com.am.common.amcommondata.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.am.common.amcommondata.document.portfolio.PortfolioDocument;
import com.am.common.amcommondata.mapper.PortfolioMapper;
import com.am.common.amcommondata.model.PortfolioModelV1;
import com.am.common.amcommondata.model.enums.PortfolioKind;
import com.am.common.amcommondata.repository.ledger.AllocationLedgerRepository;
import com.am.common.amcommondata.repository.portfolio.PortfolioDocumentRepository;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class PortfolioServiceImplPaperTest {

    @Mock PortfolioDocumentRepository repository;
    @Mock PortfolioMapper mapper;
    @Mock AllocationLedgerRepository ledger;
    @InjectMocks PortfolioServiceImpl service;

    @Test
    void paperSyncOnBrokerIsSkipped() {
        UUID id = UUID.randomUUID();
        PortfolioDocument broker = new PortfolioDocument();
        broker.setId(id.toString());
        broker.setPortfolioKind(PortfolioKind.BROKER);
        PortfolioModelV1 existing = PortfolioModelV1.builder().id(id).portfolioKind(PortfolioKind.BROKER).build();
        when(repository.findById(id.toString())).thenReturn(Optional.of(broker));
        when(mapper.toModel(broker)).thenReturn(existing);

        PortfolioModelV1 incoming = PortfolioModelV1.builder()
                .id(id).owner("u1").portfolioKind(PortfolioKind.PAPER).build();
        PortfolioModelV1 result = service.updateTradePortfolio(incoming);

        assertEquals(PortfolioKind.BROKER, result.getPortfolioKind());
        verify(repository, never()).save(any());
    }
}
