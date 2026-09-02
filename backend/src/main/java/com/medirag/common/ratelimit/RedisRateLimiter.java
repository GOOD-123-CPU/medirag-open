package com.medirag.common.ratelimit;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * 基于 Redis 的滑动窗口限流器（简单计数版）。
 *
 * 实现：INCR + EXPIRE 固定窗口。窗口边界处允许最多 2 倍突发，
 * 对保护下游 LLM 接口而言足够；如需精确平滑限流可换 Redis Cell
 * 或 Gateway 层方案。
 */
@Component
public class RedisRateLimiter {

    private final StringRedisTemplate redisTemplate;

    public RedisRateLimiter(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    /**
     * 尝试获取一次调用配额。
     *
     * @param key      限流维度键（如 rate:chat:userId:123）
     * @param limit    窗口内允许的最大次数
     * @param window   窗口时长
     * @return true=放行，false=已超限
     */
    public boolean tryAcquire(String key, int limit, Duration window) {
        try {
            Long count = redisTemplate.opsForValue().increment(key);
            if (count != null && count == 1L) {
                redisTemplate.expire(key, window);
            }
            return count == null || count <= limit;
        } catch (Exception e) {
            // Redis 不可用时放行，不因限流器故障阻断主业务
            return true;
        }
    }
}
