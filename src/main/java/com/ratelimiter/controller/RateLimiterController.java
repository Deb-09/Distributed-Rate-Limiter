package com.ratelimiter.controller;

import com.ratelimiter.model.RateLimitRequest;
import com.ratelimiter.model.RateLimitResponse;
import com.ratelimiter.service.RateLimiterService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@Slf4j
@RestController
@RequestMapping("/api/rate-limit")
@RequiredArgsConstructor
public class RateLimiterController {

    private final RateLimiterService rateLimiterService;

    @PostMapping("/check")
    public ResponseEntity<RateLimitResponse> checkRateLimit(
            @RequestBody RateLimitRequest request) {

        log.info("Received rate limit check for client: {}", request.getClientId());

        if (request.getClientId() == null || request.getClientId().isBlank()) {
            return ResponseEntity.badRequest().body(
                    RateLimitResponse.builder()
                            .allowed(false)
                            .message("clientId is required")
                            .build()
            );
        }

        RateLimitResponse response = rateLimiterService.checkRateLimit(request);

        HttpStatus status = response.isAllowed()
                ? HttpStatus.OK
                : HttpStatus.TOO_MANY_REQUESTS;

        return ResponseEntity.status(status).body(response);
    }

    @GetMapping("/health")
    public ResponseEntity<String> health() {
        return ResponseEntity.ok("Rate Limiter is up and running!");
    }

    @GetMapping("/remaining/{algorithm}/{clientId}")
    public ResponseEntity<RateLimitResponse> getRemaining(
            @PathVariable String algorithm,
            @PathVariable String clientId) {

        log.info("Checking remaining requests for client: {}", clientId);

        RateLimitRequest request = RateLimitRequest.builder()
                .clientId(clientId)
                .algorithm(algorithm)
                .build();

        RateLimitResponse response = rateLimiterService.checkRateLimit(request);

        return ResponseEntity.ok(response);
    }
}