package com.learningcompanion.shared.exception;

import java.time.Duration;

public class RateLimitExceededException extends BusinessException {

    private final Duration retryAfter;

    public RateLimitExceededException(Duration retryAfter) {
        super(ErrorCode.RATE_LIMITED);
        this.retryAfter = retryAfter;
    }

    public Duration retryAfter() {
        return retryAfter;
    }
}
