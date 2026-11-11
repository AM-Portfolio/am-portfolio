package com.portfolio.service.family;

import java.util.ArrayList;
import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import com.am.common.amcommondata.document.family.FamilyMemberDocument;
import com.am.common.amcommondata.document.portfolio.PortfolioDocument;
import com.am.common.amcommondata.repository.cas.CasImportRepository;
import com.am.common.amcommondata.repository.family.FamilyMemberRepository;
import com.am.common.amcommondata.repository.nps.NpsImportRepository;
import com.am.common.amcommondata.repository.portfolio.PortfolioDocumentRepository;
import com.portfolio.service.portfolio.BrokerPortfolioDeleteService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Cascade-delete a soft family persona: tagged broker books, CAS/NPS imports, then the member.
 * Primary (null familyMemberId) books and CAS are never touched.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FamilyMemberDeleteService {

    private final FamilyMemberRepository familyMemberRepository;
    private final PortfolioDocumentRepository portfolioDocumentRepository;
    private final CasImportRepository casImportRepository;
    private final NpsImportRepository npsImportRepository;
    private final BrokerPortfolioDeleteService brokerPortfolioDeleteService;

    public void deleteOwnedMember(String familyMemberId, String ownerUserId) {
        if (ownerUserId == null || ownerUserId.isBlank()) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Missing user");
        }
        if (familyMemberId == null || familyMemberId.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid familyMemberId");
        }

        FamilyMemberDocument member = familyMemberRepository
                .findOwnedById(familyMemberId, ownerUserId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Family member not found"));

        List<PortfolioDocument> books = portfolioDocumentRepository
                .findByOwnerAndFamilyMemberId(ownerUserId, familyMemberId);
        if (books == null) {
            books = List.of();
        }
        List<String> bookIds = new ArrayList<>();
        for (PortfolioDocument doc : books) {
            if (doc == null || doc.getId() == null) {
                continue;
            }
            bookIds.add(doc.getId());
        }
        for (String bookId : bookIds) {
            try {
                brokerPortfolioDeleteService.deleteOwnedPortfolioByDocumentId(bookId, ownerUserId);
            } catch (ResponseStatusException ex) {
                if (ex.getStatusCode() == HttpStatus.NOT_FOUND) {
                    log.warn("Family cascade: portfolio {} already gone for owner={}", bookId, ownerUserId);
                    continue;
                }
                throw ex;
            }
        }

        long casDeleted = casImportRepository.deleteByOwnerAndFamilyMemberId(ownerUserId, familyMemberId);
        long npsDeleted = npsImportRepository.deleteByOwnerAndFamilyMemberId(ownerUserId, familyMemberId);
        familyMemberRepository.delete(member);

        log.info(
                "Deleted family member id={} owner={} books={} casDocs={} npsDocs={}",
                familyMemberId, ownerUserId, bookIds.size(), casDeleted, npsDeleted);
    }
}
