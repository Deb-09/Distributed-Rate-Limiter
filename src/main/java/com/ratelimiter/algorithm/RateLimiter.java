package com.ratelimiter.algorithm;

public interface RateLimiter {

    /**
     * Check if the request is allowed for the given client
     * @param clientId - unique identifier for the client (e.g. API key, IP address)
     * @return true if request is allowed, false if rate limit exceeded
     */
    boolean isAllowed(String clientId);

    /**
     * Get remaining requests allowed for the client
     * @param clientId - unique identifier for the client
     * @return number of remaining requests
     */
    long getRemainingRequests(String clientId);

    /**
     * Get the algorithm name
     * @return algorithm name as string
     */
    String getAlgorithmName();
}