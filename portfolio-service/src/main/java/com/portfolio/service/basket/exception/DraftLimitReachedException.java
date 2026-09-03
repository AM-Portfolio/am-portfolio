package com.portfolio.service.basket.exception;

public class DraftLimitReachedException extends RuntimeException {

    public static final String ERROR_CODE = "DRAFT_LIMIT_REACHED";
    public static final int DRAFT_LIMIT = 5;

    public DraftLimitReachedException() {
        super("Maximum of " + DRAFT_LIMIT + " basket drafts reached. Delete a draft to save another.");
    }

    public DraftLimitReachedException(String message) {
        super(message);
    }
}
