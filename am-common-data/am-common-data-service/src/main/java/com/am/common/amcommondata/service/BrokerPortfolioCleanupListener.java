package com.am.common.amcommondata.service;

/**
 * Optional hook when a duplicate BROKER Mongo document is removed during heal/upsert.
 * Portfolio-kafka implements this to publish {@code am-portfolio-update} DELETE.
 */
public interface BrokerPortfolioCleanupListener {

    void onDuplicateBrokerRemoved(String ownerId, String portfolioId, String name);
}
