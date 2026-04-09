package com.ratelimiter.algorithm;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.List;

@Slf4j
@Component("slidingWindow")
public class SlidingWindowRateLimiter implements RateLimiter {

    private final StringRedisTemplate redisTemplate;
    private final long limit;
    private final long windowSizeSeconds;
    private final DefaultRedisScript<List> rateLimitScript;

    public SlidingWindowRateLimiter(
            StringRedisTemplate redisTemplate,
            @Value("${rate.limiter.sliding-window.limit:100}") long limit,
            @Value("${rate.limiter.sliding-window.window-size-seconds:60}") long windowSizeSeconds) {
        this.redisTemplate = redisTemplate;
        this.limit = limit;
        this.windowSizeSeconds = windowSizeSeconds;
        this.rateLimitScript = new DefaultRedisScript<>(buildLuaScript(), List.class);
    }

    @Override
    public boolean isAllowed(String clientId) {
        String key = "sliding_window:" + clientId;
        long currentTime = System.currentTimeMillis();

        try {
            List result = redisTemplate.execute(
                    rateLimitScript,
                    Collections.singletonList(key),
                    String.valueOf(currentTime),
                    String.valueOf(windowSizeSeconds * 1000),
                    String.valueOf(limit)
            );

            if (result != null && !result.isEmpty()) {
                long allowed = (long) result.get(0);
                long requestCount = (long) result.get(1);
                log.debug("SlidingWindow | client: {} | allowed: {} | requests in window: {}/{}",
                        clientId, allowed == 1, requestCount, limit);
                return allowed == 1;
            }
        } catch (Exception e) {
            log.error("Redis error in SlidingWindow for client {}: {}", clientId, e.getMessage());
            return true; // fail open
        }

        return false;
    }

    @Override
    public long getRemainingRequests(String clientId) {
        String key = "sliding_window:" + clientId;
        long currentTime = System.currentTimeMillis();
        long windowStart = currentTime - (windowSizeSeconds * 1000);

        try {
            redisTemplate.opsForZSet().removeRangeByScore(key, 0, windowStart);
            Long count = redisTemplate.opsForZSet().size(key);
            return limit - (count != null ? count : 0);
        } catch (Exception e) {
            log.error("Redis error getting remaining requests for client {}: {}", clientId, e.getMessage());
            return 0;
        }
    }

    @Override
    public String getAlgorithmName() {
        return "sliding_window";
    }

    private String buildLuaScript() {
        return """
                local key = KEYS[1]
                local current_time = tonumber(ARGV[1])
                local window_size = tonumber(ARGV[2])
                local limit = tonumber(ARGV[3])
                
                local window_start = current_time - window_size
                
                redis.call('ZREMRANGEBYSCORE', key, 0, window_start)
                
                local request_count = redis.call('ZCARD', key)
                
                if request_count < limit then
                    redis.call('ZADD', key, current_time, current_time)
                    redis.call('EXPIRE', key, math.ceil(window_size / 1000) + 1)
                    return {1, request_count + 1}
                else
                    return {0, request_count}
                end
                """;
    }
}