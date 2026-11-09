package com.portfolio.service.event;

/**
 * Fan-out after a broker portfolio is removed from Mongo (Kafka {@code am-portfolio-update}).
 */
public interface PortfolioDeleteNotifier {

    /**
     * @param ownerId     portfolio owner (JWT / event userId)
     * @param portfolioId Mongo document UUID string
     * @param name        human-readable name when known
     * @param source      producer label for the outbound event
     */
    void notifyDeleted(String ownerId, String portfolioId, String name, String source);
}
