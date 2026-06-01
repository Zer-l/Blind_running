package com.guiderun.app.domain.repository

import com.guiderun.app.domain.model.GeoPoint
import kotlinx.coroutines.flow.Flow

/**
 * 定位能力抽象接口（Domain 层）。
 *
 * 实现位于 data/location/FusedLocationProvider，基于 FusedLocationProviderClient。
 * 通过接口隔离 Domain 层与 Google Play Services，便于单测 Mock 注入。
 */
interface LocationProvider {
    /** Returns the most recently cached location, or null if unavailable. */
    suspend fun getLastLocation(): GeoPoint?

    /**
     * Emits location updates at approximately [intervalMs] milliseconds.
     * Cancel the collecting coroutine to stop updates.
     *
     * 返回冷 Flow：每次 collect 开始一条独立位置订阅；取消 collect（lifecycleScope / viewModelScope 取消）
     * 时自动注销 LocationCallback，不会有内存泄漏。
     */
    fun locationUpdates(intervalMs: Long = 5_000): Flow<GeoPoint>
}
