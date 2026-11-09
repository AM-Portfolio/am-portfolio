package com.portfolio.service.basket.exception;

public class DraftLimitReachedException extends RuntimeException {

    public static final String ERROR_CODE = "DRAFT_LIMIT_REACHED";

    public DraftLimitReachedException() {
        this(5);
    }

    public DraftLimitReachedException(int limit) {
        super("Maximum of " + limit + " basket drafts reached. Delete a draft to save another.");
    }

    public DraftLimitReachedException(String message) {
        super(message);
    }
}
