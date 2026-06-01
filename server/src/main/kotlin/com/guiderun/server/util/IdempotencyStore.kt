package com.guiderun.server.util

import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import java.util.concurrent.ConcurrentHashMap

/**
 * 进程内幂等键缓存：相同 key 在 TTL 内返回上次结果，避免重复创单/重复扣款。
 *
 * - 实现：`ConcurrentHashMap`，TTL=24h，定时清理 6h 前条目
 * - 适用范围：单机；多实例部署需替换为 Redis（接口语义保留）
 */
@Component
class IdempotencyStore {

    private companion object {
        const val TTL_MS = 24 * 3600 * 1000L
        const val EVICT_THRESHOLD_MS = 6 * 3600 * 1000L
    }

    private val store = ConcurrentHashMap<String, Pair<Any, Long>>()

    /** 若 key 命中且未过期则返回缓存值，否则执行 [compute] 并缓存结果。 */
    @Suppress("UNCHECKED_CAST")
    fun <T : Any> getOrPut(key: String, compute: () -> T): T {
        val cached = store[key]
        if (cached != null && System.currentTimeMillis() - cached.second < TTL_MS) {
            return cached.first as T
        }
        val result = compute()
        store[key] = result to System.currentTimeMillis()
        return result
    }

    /** 每小时清除 6 小时前的过期 key。 */
    @Scheduled(fixedDelay = 3_600_000L)
    fun evictExpired() {
        val threshold = System.currentTimeMillis() - EVICT_THRESHOLD_MS
        store.entries.removeIf { it.value.second < threshold }
    }
}
