package com.portfolio.service.cas;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;

import com.am.common.amcommondata.document.cas.CasImportDocument;
import com.am.common.amcommondata.model.cas.CasDocumentMeta;
import com.am.common.amcommondata.model.cas.CasMfFolioGroup;
import com.am.common.amcommondata.model.demat.DematAccountModel;
import com.am.common.amcommondata.repository.cas.CasImportRepository;
import com.portfolio.model.events.CasDocumentEvent;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class CasImportService {

    private final CasImportRepository casImportRepository;

    public CasImportDocument upsertFromEvent(CasDocumentEvent event) {
        if (event == null || event.getUserId() == null || event.getUserId().isBlank()) {
            throw new IllegalArgumentException("CAS_DOCUMENT requires userId");
        }
        String owner = event.getUserId();
        CasImportDocument existing = casImportRepository
                .findFirstByOwnerOrderByAsOfDateDescImportedAtDesc(owner)
                .orElse(null);

        CasImportDocument doc = existing != null ? existing : CasImportDocument.builder().build();
        doc.setOwner(owner);
        doc.setDocumentSource(event.getDocumentSource() != null ? event.getDocumentSource() : "CDSL_CAS");
        doc.setAsOfDate(event.getAsOfDate());
        doc.setStatementFrom(event.getStatementFrom());
        doc.setStatementTo(event.getStatementTo());
        doc.setImportedAt(LocalDateTime.now());
        doc.setCover(event.getCover());
        doc.setValidation(event.getValidation());
        doc.setAccountDirectory(event.getAccountDirectory());
        doc.setMfFolios(event.getMfFolios());
        doc.setUnmappedDematHoldings(event.getUnmappedDematHoldings());

        CasImportDocument saved = casImportRepository.save(doc);
        log.info("Upserted CAS import for owner={} asOf={} directoryRows={}",
                owner, saved.getAsOfDate(),
                saved.getAccountDirectory() != null ? saved.getAccountDirectory().size() : 0);
        return saved;
    }

    public Optional<CasImportDocument> latest(String userId) {
        return casImportRepository.findFirstByOwnerOrderByAsOfDateDescImportedAtDesc(userId);
    }

    public List<DematAccountModel> accounts(String userId) {
        return latest(userId)
                .map(CasImportDocument::getAccountDirectory)
                .orElse(Collections.emptyList());
    }

    public Optional<DematAccountModel> account(String userId, String accountNo) {
        if (accountNo == null) {
            return Optional.empty();
        }
        return accounts(userId).stream()
                .filter(a -> accountNo.equals(a.getAccountNo())
                        || accountNo.equals(a.getBoid())
                        || accountNo.equals(a.getFolio()))
                .findFirst();
    }

    public List<CasMfFolioGroup> folios(String userId) {
        return latest(userId)
                .map(CasImportDocument::getMfFolios)
                .orElse(Collections.emptyList());
    }

    public Optional<CasMfFolioGroup> folio(String userId, String folio) {
        if (folio == null) {
            return Optional.empty();
        }
        return folios(userId).stream()
                .filter(f -> folio.equals(f.getFolio()))
                .findFirst();
    }

    public Optional<CasDocumentMeta> latestMeta(String userId) {
        return latest(userId).map(doc -> CasDocumentMeta.builder()
                .documentSource(doc.getDocumentSource())
                .asOfDate(doc.getAsOfDate())
                .statementFrom(doc.getStatementFrom())
                .statementTo(doc.getStatementTo())
                .importedAt(doc.getImportedAt())
                .cover(doc.getCover())
                .validation(doc.getValidation())
                .build());
    }

    public List<CasMfFolioGroup> foliosForPortfolio(String userId, String portfolioIdHint) {
        // Folio groups are user-scoped from CAS; portfolioId is used by API for CDSL books only.
        return folios(userId).stream()
                .filter(f -> f != null)
                .collect(Collectors.toList());
    }
}
