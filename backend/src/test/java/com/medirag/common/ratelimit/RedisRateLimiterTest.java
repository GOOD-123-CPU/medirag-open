package com.medirag.common.ratelimit;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * RedisRateLimiter 单元测试：固定窗口计数、超限拒绝、Redis 故障 fail-open。
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class RedisRateLimiterTest {

    @Mock
    private StringRedisTemplate redisTemplate;

    @Mock
    private ValueOperations<String, String> valueOps;

    private RedisRateLimiter limiter;

    @BeforeEach
    void setUp() {
        limiter = new RedisRateLimiter(redisTemplate);
        lenient().when(redisTemplate.opsForValue()).thenReturn(valueOps);
    }

    private void stubCount(Long count) {
        when(valueOps.increment("rate:chat:1")).thenReturn(count);
    }

    @Test
    @DisplayName("窗口内首次请求放行并设置过期时间")
    void firstRequestAllowedAndExpiresSet() {
        stubCount(1L);
        assertTrue(limiter.tryAcquire("rate:chat:1", 10, Duration.ofMinutes(1)));
        verify(valueOps).increment("rate:chat:1");
        verify(redisTemplate).expire("rate:chat:1", Duration.ofMinutes(1));
    }

    @Test
    @DisplayName("未超限的后续请求放行且不重复设置过期")
    void subsequentRequestAllowedWithoutReExpire() {
        stubCount(5L);
        assertTrue(limiter.tryAcquire("rate:chat:1", 10, Duration.ofMinutes(1)));
        verify(redisTemplate, never()).expire(anyString(), any(Duration.class));
    }

    @Test
    @DisplayName("达到上限时拒绝并仍放行边界值")
    void limitBoundary() {
        stubCount(10L);
        assertTrue(limiter.tryAcquire("rate:chat:1", 10, Duration.ofMinutes(1)), "第 10 次应放行（边界）");
        stubCount(11L);
        assertFalse(limiter.tryAcquire("rate:chat:1", 10, Duration.ofMinutes(1)), "第 11 次应拒绝");
    }

    @Test
    @DisplayName("Redis 抛异常时 fail-open 放行，不阻断主业务")
    void failOpenOnRedisError() {
        when(valueOps.increment("rate:chat:1")).thenThrow(new IllegalStateException("Redis down"));
        assertTrue(limiter.tryAcquire("rate:chat:1", 10, Duration.ofMinutes(1)));
    }

    @Test
    @DisplayName("increment 返回 null 时保守放行")
    void allowNullCount() {
        stubCount(null);
        assertTrue(limiter.tryAcquire("rate:chat:1", 10, Duration.ofMinutes(1)));
    }
}
