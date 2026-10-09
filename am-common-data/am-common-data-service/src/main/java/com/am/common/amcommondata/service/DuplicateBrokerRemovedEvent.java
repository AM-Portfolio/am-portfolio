package com.am.common.amcommondata.service;

/**
 * Fired when upsert cleans a duplicate BROKER Mongo document (e.g. GROW beside GROWW).
 * portfolio-kafka listens and publishes {@code am-portfolio-update} DELETE.
 */
public record DuplicateBrokerRemovedEvent(String ownerId, String portfolioId, String name) {
}
