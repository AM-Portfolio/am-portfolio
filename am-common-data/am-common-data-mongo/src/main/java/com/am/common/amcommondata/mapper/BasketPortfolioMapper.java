package com.am.common.amcommondata.mapper;

import com.am.common.amcommondata.document.basket.BasketPortfolioDocument;
import com.am.common.amcommondata.document.portfolio.PortfolioDocument;
import com.am.common.amcommondata.model.PortfolioModelV1;
import com.am.common.amcommondata.model.enums.PortfolioKind;

/**
 * Dual-read / dual-write for basket fields: nested {@code basket} subdocument vs legacy flat fields.
 */
public final class BasketPortfolioMapper {

    private BasketPortfolioMapper() {
    }

    public static void applyBasketFieldsToModel(PortfolioModelV1 model, PortfolioDocument document) {
        if (model == null || document == null) {
            return;
        }
        BasketPortfolioDocument nested = document.getBasket();

        model.setSourcePortfolioId(firstNonBlank(
                nested != null ? nested.getSourcePortfolioId() : null,
                document.getSourcePortfolioId()));
        model.setEtfIsin(firstNonBlank(
                nested != null ? nested.getEtfIsin() : null,
                document.getEtfIsin()));
        model.setEtfName(firstNonBlank(
                nested != null ? nested.getEtfName() : null,
                document.getEtfName()));
        model.setCreatedFromBasketAt(firstNonNull(
                nested != null ? nested.getCreatedFromBasketAt() : null,
                document.getCreatedFromBasketAt()));
        model.setGapMissingCount(firstNonNull(
                nested != null ? nested.getGapMissingCount() : null,
                document.getGapMissingCount()));
        model.setInvestmentAmount(firstNonNull(
                nested != null ? nested.getInvestmentAmount() : null,
                document.getInvestmentAmount()));
        model.setReplicaScore(firstNonNull(
                nested != null ? nested.getReplicaScore() : null,
                document.getReplicaScore()));
        model.setCoverageAfterCreation(firstNonNull(
                nested != null ? nested.getCoverageAfterCreation() : null,
                document.getCoverageAfterCreation()));
    }

    public static void applyBasketFieldsToDocument(PortfolioDocument document, PortfolioModelV1 model) {
        if (document == null || model == null) {
            return;
        }
        if (!PortfolioKind.isBasket(PortfolioKind.orBroker(model.getPortfolioKind()))) {
            return;
        }

        document.setSourcePortfolioId(model.getSourcePortfolioId());
        document.setEtfIsin(model.getEtfIsin());
        document.setEtfName(model.getEtfName());
        document.setCreatedFromBasketAt(model.getCreatedFromBasketAt());
        document.setGapMissingCount(model.getGapMissingCount());
        document.setInvestmentAmount(model.getInvestmentAmount());
        document.setReplicaScore(model.getReplicaScore());
        document.setCoverageAfterCreation(model.getCoverageAfterCreation());

        document.setBasket(toNestedDocument(model));
    }

    static BasketPortfolioDocument toNestedDocument(PortfolioModelV1 model) {
        if (model == null) {
            return null;
        }
        return BasketPortfolioDocument.builder()
                .sourcePortfolioId(model.getSourcePortfolioId())
                .etfIsin(model.getEtfIsin())
                .etfName(model.getEtfName())
                .createdFromBasketAt(model.getCreatedFromBasketAt())
                .gapMissingCount(model.getGapMissingCount())
                .investmentAmount(model.getInvestmentAmount())
                .replicaScore(model.getReplicaScore())
                .coverageAfterCreation(model.getCoverageAfterCreation())
                .build();
    }

    private static String firstNonBlank(String primary, String fallback) {
        if (primary != null && !primary.isBlank()) {
            return primary;
        }
        return fallback;
    }

    private static <T> T firstNonNull(T primary, T fallback) {
        return primary != null ? primary : fallback;
    }
}
