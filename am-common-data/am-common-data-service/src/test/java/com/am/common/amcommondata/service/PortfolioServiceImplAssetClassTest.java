package com.am.common.amcommondata.service;

import com.am.common.amcommondata.document.asset.AssetDocument;
import com.am.common.amcommondata.document.asset.equity.EquityDocument;
import com.am.common.amcommondata.document.common.AuditMetadata;
import com.am.common.amcommondata.document.portfolio.PortfolioDocument;
import com.am.common.amcommondata.mapper.PortfolioMapper;
import com.am.common.amcommondata.model.PortfolioModelV1;
import com.am.common.amcommondata.model.asset.AssetModel;
import com.am.common.amcommondata.model.enums.AssetType;
import com.am.common.amcommondata.model.enums.BrokerType;
import com.am.common.amcommondata.model.enums.PortfolioKind;
import com.am.common.amcommondata.repository.ledger.AllocationLedgerRepository;
import com.am.common.amcommondata.repository.portfolio.PortfolioDocumentRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PortfolioServiceImplAssetClassTest {

    @Mock
    private PortfolioDocumentRepository portfolioDocumentRepository;
    @Mock
    private PortfolioMapper portfolioMapper;
    @Mock
    private AllocationLedgerRepository allocationLedgerRepository;

    @InjectMocks
    private PortfolioServiceImpl portfolioService;

    private final UUID portfolioId = UUID.fromString("bd326ea4-bc7f-3ca6-8709-bbcc6645478d");
    private final String owner = "user-1";

    private PortfolioDocument existingDoc;

    @BeforeEach
    void setUp() {
        existingDoc = PortfolioDocument.builder()
                .id(portfolioId.toString())
                .name("UPSTOX")
                .owner(owner)
                .brokerType(BrokerType.UPSTOX)
                .portfolioKind(PortfolioKind.BROKER)
                .equities(List.of(EquityDocument.builder().symbol("RELIANCE").quantity(1.0).currentValue(1000.0).build()))
                .bonds(List.of(AssetDocument.builder().symbol("GSEC").name("G-Sec").quantity(1.0).currentValue(50000.0)
                        .assetType(AssetType.FIXED_INCOME).build()))
                .cash(new ArrayList<>())
                .mutualFunds(new ArrayList<>())
                .commodities(new ArrayList<>())
                .totalValue(51000.0)
                .audit(AuditMetadata.builder().version(1L).build())
                .build();
    }

    @Test
    void replaceCash_keepsBondsAndEquities() {
        when(portfolioDocumentRepository.findById(portfolioId.toString())).thenReturn(Optional.of(existingDoc));
        when(portfolioMapper.toDocument(any(PortfolioModelV1.class))).thenAnswer(inv -> {
            PortfolioModelV1 m = inv.getArgument(0);
            List<AssetDocument> cashDocs = new ArrayList<>();
            if (m.getCash() != null) {
                for (AssetModel a : m.getCash()) {
                    cashDocs.add(AssetDocument.builder()
                            .symbol(a.getSymbol())
                            .name(a.getName())
                            .quantity(a.getQuantity())
                            .currentValue(a.getCurrentValue())
                            .assetType(a.getAssetType())
                            .build());
                }
            }
            return PortfolioDocument.builder().cash(cashDocs).build();
        });
        when(portfolioDocumentRepository.save(any(PortfolioDocument.class))).thenAnswer(inv -> inv.getArgument(0));
        when(portfolioMapper.toModel(any(PortfolioDocument.class))).thenAnswer(inv -> {
            PortfolioDocument d = inv.getArgument(0);
            List<AssetModel> cashModels = new ArrayList<>();
            if (d.getCash() != null) {
                for (AssetDocument a : d.getCash()) {
                    cashModels.add(AssetModel.builder().symbol(a.getSymbol()).name(a.getName())
                            .currentValue(a.getCurrentValue()).assetType(a.getAssetType()).build());
                }
            }
            return PortfolioModelV1.builder()
                    .id(portfolioId)
                    .owner(owner)
                    .cash(cashModels)
                    .bonds(List.of(AssetModel.builder().symbol("GSEC").currentValue(50000.0).build()))
                    .totalValue(d.getTotalValue())
                    .build();
        });

        AssetModel cashRow = AssetModel.builder()
                .name("INR Cash")
                .symbol("INR")
                .quantity(1.0)
                .currentValue(25000.0)
                .build();

        PortfolioModelV1 saved = portfolioService.replaceAssetClassList(
                portfolioId, owner, "cash", List.of(cashRow));

        ArgumentCaptor<PortfolioDocument> cap = ArgumentCaptor.forClass(PortfolioDocument.class);
        verify(portfolioDocumentRepository).save(cap.capture());
        PortfolioDocument written = cap.getValue();
        assertEquals(1, written.getCash().size());
        assertEquals(1, written.getBonds().size());
        assertEquals(1, written.getEquities().size());
        assertTrue(written.getTotalValue() >= 75000.0);
        assertEquals(1, saved.getCash().size());
    }

    @Test
    void replaceCash_wrongOwner_throws() {
        when(portfolioDocumentRepository.findById(portfolioId.toString())).thenReturn(Optional.of(existingDoc));
        assertThrows(SecurityException.class, () ->
                portfolioService.replaceAssetClassList(portfolioId, "other-user", "cash", List.of()));
    }

    @Test
    void replace_invalidClass_throws() {
        assertThrows(IllegalArgumentException.class, () ->
                portfolioService.replaceAssetClassList(portfolioId, owner, "mutualFunds", List.of()));
    }
}
