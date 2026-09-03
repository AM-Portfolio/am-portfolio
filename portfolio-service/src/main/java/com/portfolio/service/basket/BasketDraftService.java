package com.portfolio.service.basket;

import com.am.common.amcommondata.document.basket.BasketDraftDocument;
import com.am.common.amcommondata.repository.basket.BasketDraftRepository;
import com.portfolio.service.basket.dto.BasketDraftDtos.BasketDraftDetailDto;
import com.portfolio.service.basket.dto.BasketDraftDtos.BasketDraftListResponse;
import com.portfolio.service.basket.dto.BasketDraftDtos.BasketDraftSummaryDto;
import com.portfolio.service.basket.dto.BasketDraftDtos.UpsertBasketDraftRequest;
import com.portfolio.service.basket.exception.DraftLimitReachedException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class BasketDraftService {

    public static final int DRAFT_LIMIT = DraftLimitReachedException.DRAFT_LIMIT;

    private final BasketDraftRepository basketDraftRepository;

    public BasketDraftListResponse listDrafts(String userId, String portfolioId) {
        requireUserId(userId);
        List<BasketDraftDocument> docs = basketDraftRepository.findByUserIdOrderByUpdatedAtDesc(userId);
        if (portfolioId != null && !portfolioId.isBlank()) {
            docs = docs.stream()
                    .filter(d -> portfolioId.equals(d.getSourcePortfolioId()))
                    .collect(Collectors.toList());
        }
        long totalForUser = basketDraftRepository.countByUserId(userId);
        return BasketDraftListResponse.builder()
                .drafts(docs.stream().map(this::toSummary).collect(Collectors.toList()))
                .draftCount((int) totalForUser)
                .draftLimit(DRAFT_LIMIT)
                .build();
    }

    public BasketDraftDetailDto getDraft(String draftId, String userId) {
        requireUserId(userId);
        BasketDraftDocument doc = basketDraftRepository.findByIdAndUserId(draftId, userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Draft not found"));
        return toDetail(doc);
    }

    public BasketDraftDetailDto upsert(UpsertBasketDraftRequest request) {
        if (request == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Request required");
        }
        requireUserId(request.getUserId());
        if (request.getSourcePortfolioId() == null || request.getSourcePortfolioId().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "sourcePortfolioId required");
        }
        if (request.getEtfIsin() == null || request.getEtfIsin().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "etfIsin required");
        }

        LocalDateTime now = LocalDateTime.now();
        BasketDraftDocument existing = null;

        if (request.getDraftId() != null && !request.getDraftId().isBlank()) {
            existing = basketDraftRepository
                    .findByIdAndUserId(request.getDraftId(), request.getUserId())
                    .orElse(null);
            if (existing == null) {
                throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Draft not found");
            }
        }

        if (existing == null) {
            existing = basketDraftRepository
                    .findByUserIdAndSourcePortfolioIdAndEtfIsin(
                            request.getUserId(),
                            request.getSourcePortfolioId(),
                            request.getEtfIsin())
                    .orElse(null);
        }

        boolean isNew = existing == null;
        if (isNew) {
            long count = basketDraftRepository.countByUserId(request.getUserId());
            if (count >= DRAFT_LIMIT) {
                throw new DraftLimitReachedException();
            }
            existing = BasketDraftDocument.builder()
                    .id(UUID.randomUUID().toString())
                    .userId(request.getUserId())
                    .createdAt(now)
                    .build();
        }

        existing.setSourcePortfolioId(request.getSourcePortfolioId());
        existing.setEtfIsin(request.getEtfIsin());
        existing.setEtfName(request.getEtfName());
        existing.setBasketName(request.getBasketName());
        existing.setInvestmentAmount(request.getInvestmentAmount());
        existing.setReplicaScore(request.getReplicaScore());
        existing.setHasCalculated(request.getHasCalculated());
        existing.setExcludedSymbols(request.getExcludedSymbols());
        existing.setManualQtyOverrides(request.getManualQtyOverrides());
        existing.setOpportunity(request.getOpportunity());
        existing.setUpdatedAt(now);

        BasketDraftDocument saved = basketDraftRepository.save(existing);

        if (isNew) {
            long recount = basketDraftRepository.countByUserId(request.getUserId());
            if (recount > DRAFT_LIMIT) {
                basketDraftRepository.deleteByIdAndUserId(saved.getId(), request.getUserId());
                throw new DraftLimitReachedException();
            }
        }

        return toDetail(saved);
    }

    public void deleteDraft(String draftId, String userId) {
        requireUserId(userId);
        long deleted = basketDraftRepository.deleteByIdAndUserId(draftId, userId);
        if (deleted == 0) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Draft not found");
        }
    }

    /**
     * Best-effort delete after successful create (or idempotent replay). Never throws.
     */
    public void deleteAfterCreate(String userId, String draftId, String sourcePortfolioId, String etfIsin) {
        if (userId == null || userId.isBlank()) {
            return;
        }
        try {
            if (draftId != null && !draftId.isBlank()) {
                basketDraftRepository.deleteByIdAndUserId(draftId, userId);
            }
            if (sourcePortfolioId != null && etfIsin != null) {
                basketDraftRepository.deleteByUserIdAndSourcePortfolioIdAndEtfIsin(
                        userId, sourcePortfolioId, etfIsin);
            }
        } catch (Exception e) {
            log.warn("Draft cleanup after create fail-open: {}", e.getMessage());
        }
    }

    private void requireUserId(String userId) {
        if (userId == null || userId.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "userId required");
        }
    }

    private BasketDraftSummaryDto toSummary(BasketDraftDocument doc) {
        return BasketDraftSummaryDto.builder()
                .id(doc.getId())
                .sourcePortfolioId(doc.getSourcePortfolioId())
                .etfIsin(doc.getEtfIsin())
                .etfName(doc.getEtfName())
                .basketName(doc.getBasketName())
                .investmentAmount(doc.getInvestmentAmount())
                .replicaScore(doc.getReplicaScore())
                .hasCalculated(doc.getHasCalculated())
                .updatedAt(doc.getUpdatedAt())
                .createdAt(doc.getCreatedAt())
                .build();
    }

    private BasketDraftDetailDto toDetail(BasketDraftDocument doc) {
        return BasketDraftDetailDto.builder()
                .id(doc.getId())
                .userId(doc.getUserId())
                .sourcePortfolioId(doc.getSourcePortfolioId())
                .etfIsin(doc.getEtfIsin())
                .etfName(doc.getEtfName())
                .basketName(doc.getBasketName())
                .investmentAmount(doc.getInvestmentAmount())
                .replicaScore(doc.getReplicaScore())
                .hasCalculated(doc.getHasCalculated())
                .excludedSymbols(doc.getExcludedSymbols())
                .manualQtyOverrides(doc.getManualQtyOverrides())
                .opportunity(doc.getOpportunity())
                .createdAt(doc.getCreatedAt())
                .updatedAt(doc.getUpdatedAt())
                .build();
    }
}
