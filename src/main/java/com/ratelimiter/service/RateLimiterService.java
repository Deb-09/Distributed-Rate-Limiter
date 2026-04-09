package com.ratelimiter.service;

import com.ratelimiter.algorithm.RateLimiter;
import com.ratelimiter.model.RateLimitRequest;
import com.ratelimiter.model.RateLimitResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

@Slf4j
@Service
public class RateLimiterService {

    private final RateLimiter tokenBucketRateLimiter;
    private final RateLimiter slidingWindowRateLimiter;

    public RateLimiterService(
            @Qualifier("tokenBucket") RateLimiter tokenBucketRateLimiter,
            @Qualifier("slidingWindow") RateLimiter slidingWindowRateLimiter) {
        this.tokenBucketRateLimiter = tokenBucketRateLimiter;
        this.slidingWindowRateLimiter = slidingWindowRateLimiter;
    }

    public RateLimitResponse checkRateLimit(RateLimitRequest request) {
        log.debug("Checking rate limit for client: {} using algorithm: {}",
                request.getClientId(), request.getAlgorithm());

        RateLimiter rateLimiter = selectAlgorithm(request.getAlgorithm());

        boolean allowed = rateLimiter.isAllowed(request.getClientId());
        long remaining = rateLimiter.getRemainingRequests(request.getClientId());

        return RateLimitResponse.builder()
                .allowed(allowed)
                .remainingRequests(remaining)
                .algorithm(rateLimiter.getAlgorithmName())
                .clientId(request.getClientId())
                .message(allowed ? "Request allowed" : "Rate limit exceeded. Try again later.")
                .build();
    }

    private RateLimiter selectAlgorithm(String algorithm) {
        if (algorithm == null) {
            log.debug("No algorithm specified, defaulting to token_bucket");
            return tokenBucketRateLimiter;
        }

        return switch (algorithm.toLowerCase()) {
            case "sliding_window" -> slidingWindowRateLimiter;
            default -> {
                log.debug("Unknown algorithm '{}', defaulting to token_bucket", algorithm);
                yield tokenBucketRateLimiter;
            }
        };
    }
}