package com.portfolio.basket.kernel;

import com.portfolio.basket.model.BasketOpportunity;
import com.portfolio.basket.model.BasketOpportunity.BasketItem;
import com.portfolio.basket.model.BasketOpportunity.ItemStatus;
import com.portfolio.basket.util.BasketUtils;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class BasketScoreCalculator {

    public void applyCompositionScores(BasketOpportunity opportunity, double investmentAmount, boolean includeHeld) {
        if (opportunity == null || opportunity.getComposition() == null) {
            return;
        }
        int total = (int) opportunity.getComposition().stream()
                .filter(i -> i.getStatus() != ItemStatus.EXCLUDED)
                .count();
        int matchCount = 0;
        double replicaTotal = 0.0;
        for (BasketItem item : opportunity.getComposition()) {
            if (item.getStatus() == ItemStatus.EXCLUDED) {
                continue;
            }
            double price = item.getLastPrice() != null ? item.getLastPrice() : 0.0;

            if (item.getStatus() == ItemStatus.HELD || item.getStatus() == ItemStatus.SUBSTITUTE) {
                matchCount++;
                double allocatedQty = item.getTargetQuantity() != null ? item.getTargetQuantity() : 0.0;
                double contribution = allocatedQty * price;
                if (!includeHeld && item.getBuyQuantity() != null && item.getBuyQuantity() > 0) {
                    contribution += item.getBuyQuantity() * price;
                }
                double itemWeight = investmentAmount > 0
                        ? BasketUtils.round((contribution / investmentAmount) * 100.0) : 0.0;
                item.setReplicaWeight(itemWeight);
                replicaTotal += itemWeight;
            } else if (item.getStatus() == ItemStatus.MISSING) {
                if (item.getBuyQuantity() != null && item.getBuyQuantity() > 0) {
                    double buyValue = item.getBuyQuantity() * price;
                    double itemWeight = investmentAmount > 0
                            ? BasketUtils.round((buyValue / investmentAmount) * 100.0) : 0.0;
                    item.setReplicaWeight(itemWeight);
                    replicaTotal += itemWeight;
                } else {
                    item.setReplicaWeight(0.0);
                }
            } else {
                item.setReplicaWeight(0.0);
            }
        }
        opportunity.setMatchScore(BasketUtils.round(total == 0 ? 0 : (double) matchCount / total * 100.0));
        opportunity.setReplicaScore(BasketUtils.round(Math.min(replicaTotal, 100.0)));
        opportunity.setReadyToReplicate(replicaTotal >= 90.0);
        opportunity.setHeldCount(matchCount);
    }

    public double computeOverlapMatchScore(int matchCount, int total) {
        return BasketUtils.round(total == 0 ? 0 : (double) matchCount / total * 100.0);
    }

    public double computeReplicaScore(List<BasketItem> composition) {
        if (composition == null) {
            return 0.0;
        }
        double heldScore = composition.stream().filter(i -> i.getStatus() == ItemStatus.HELD)
                .mapToDouble(i -> i.getEtfWeight() != null ? i.getEtfWeight() : 0.0).sum();
        double subScore = composition.stream().filter(i -> i.getStatus() == ItemStatus.SUBSTITUTE)
                .mapToDouble(i -> i.getEtfWeight() != null ? i.getEtfWeight() : 0.0).sum();
        return BasketUtils.round(heldScore + subScore);
    }
}
