package com.guiderun.app.domain.repository

import com.guiderun.app.domain.model.GeoPoint
import kotlinx.coroutines.flow.Flow

interface LocationProvider {
    /** Returns the most recently cached location, or null if unavailable. */
    suspend fun getLastLocation(): GeoPoint?

    /**
     * Emits location updates at approximately [intervalMs] milliseconds.
     * Cancel the collecting coroutine to stop updates.
     */
    fun locationUpdates(intervalMs: Long = 5_000): Flow<GeoPoint>
}
