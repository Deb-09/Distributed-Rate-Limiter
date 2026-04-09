package com.ratelimiter.algorithm;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.List;

@Slf4j
@Component("tokenBucket")
public class TokenBucketRateLimiter implements RateLimiter {

    private final StringRedisTemplate redisTemplate;
    private final long capacity;
    private final long refillRate;
    private final DefaultRedisScript<List> rateLimitScript;

    public TokenBucketRateLimiter(
            StringRedisTemplate redisTemplate,
            @Value("${rate.limiter.token-bucket.capacity:10}") long capacity,
            @Value("${rate.limiter.token-bucket.refill-rate:10}") long refillRate) {
        this.redisTemplate = redisTemplate;
        this.capacity = capacity;
        this.refillRate = refillRate;
        this.rateLimitScript = new DefaultRedisScript<>(buildLuaScript(), List.class);
    }

    @Override
    public boolean isAllowed(String clientId) {
        String key = "token_bucket:" + clientId;
        long currentTime = System.currentTimeMillis();

        try {
            List result = redisTemplate.execute(
                    rateLimitScript,
                    Collections.singletonList(key),
                    String.valueOf(capacity),
                    String.valueOf(refillRate),
                    String.valueOf(currentTime)
            );

            if (result != null && !result.isEmpty()) {
                long allowed = (long) result.get(0);
                log.debug("TokenBucket | client: {} | allowed: {} | tokens remaining: {}",
                        clientId, allowed == 1, result.get(1));
                return allowed == 1;
            }
        } catch (Exception e) {
            log.error("Redis error in TokenBucket for client {}: {}", clientId, e.getMessage());
            return true; // fail open - allow request if Redis is down
        }

        return false;
    }

@Override
public long getRemainingRequests(String clientId) {
    String key = "token_bucket:" + clientId;
    try {
        Object tokens = redisTemplate.opsForHash().get(key, "tokens");
        log.debug("Raw token value from Redis for {}: {}", clientId, tokens);
        if (tokens != null) {
            return Long.parseLong(tokens.toString());
        }
        return capacity;
    } catch (Exception e) {
        log.error("Redis error getting remaining requests for client {}: {}", clientId, e.getMessage());
        return capacity;
    }
}

    @Override
    public String getAlgorithmName() {
        return "token_bucket";
    }

    private String buildLuaScript() {
        return """
                local key = KEYS[1]
                local capacity = tonumber(ARGV[1])
                local refill_rate = tonumber(ARGV[2])
                local current_time = tonumber(ARGV[3])
                
                local bucket = redis.call('HMGET', key, 'tokens', 'last_refill')
                local tokens = tonumber(bucket[1])
                local last_refill = tonumber(bucket[2])
                
                if tokens == nil then
                    tokens = capacity
                    last_refill = current_time
                end
                
                local elapsed = (current_time - last_refill) / 1000
                local refill_amount = math.floor(elapsed * refill_rate)
                tokens = math.min(capacity, tokens + refill_amount)
                
                if refill_amount > 0 then
                    last_refill = current_time
                end
                
                if tokens > 0 then
                    tokens = tokens - 1
                    redis.call('HMSET', key, 'tokens', tokens, 'last_refill', last_refill)
                    redis.call('EXPIRE', key, 3600)
                    return {1, tokens}
                else
                    redis.call('HMSET', key, 'tokens', tokens, 'last_refill', last_refill)
                    redis.call('EXPIRE', key, 3600)
                    return {0, tokens}
                end
                """;
    }
}