package com.guiderun.server.service

import com.guiderun.server.dto.run.RunTrackResponseDto
import com.guiderun.server.dto.run.TrackPointDto
import com.guiderun.server.dto.run.UploadTracksDto
import com.guiderun.server.entity.RunTrackEntity
import com.guiderun.server.entity.TrackPointJson
import com.guiderun.server.entity.TrackRole
import com.guiderun.server.exception.AppException
import com.guiderun.server.exception.ErrorCode
import com.guiderun.server.repository.RunTrackJpaRepository
import com.guiderun.server.repository.RunRequestJpaRepository
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant
import kotlin.math.*

@Service
@Transactional
class RunTrackService(
    private val trackRepo: RunTrackJpaRepository,
    private val requestRepo: RunRequestJpaRepository,
) {

    fun uploadTracks(userId: String, requestId: String, dto: UploadTracksDto): RunTrackResponseDto {
        val request = requestRepo.findById(requestId).orElseThrow {
            AppException(ErrorCode.REQUEST_NOT_FOUND, "订单不存在", HttpStatus.NOT_FOUND)
        }
        val role = parseRole(dto.role)
        validateParticipant(userId, request.blindRunnerId, request.volunteerId, role)

        val now = Instant.now().toEpochMilli()
        val validPoints = dto.points
            .filter { p -> p.t <= now && p.lat in -90.0..90.0 && p.lng in -180.0..180.0 }
            .map { p -> TrackPointJson(t = p.t, lat = p.lat, lng = p.lng, acc = p.acc, spd = p.spd) }

        val existing = trackRepo.findByRequestIdAndUserId(requestId, userId)
        return if (existing == null) {
            val entity = RunTrackEntity(
                requestId = requestId,
                userId = userId,
                role = role,
                points = validPoints,
            )
            recalcStats(entity)
            trackRepo.save(entity).toDto()
        } else {
            val existingTs = existing.points.mapTo(HashSet()) { it.t }
            val merged = existing.points + validPoints.filter { it.t !in existingTs }
            val sorted = merged.sortedBy { it.t }
            existing.points = sorted
            recalcStats(existing)
            trackRepo.save(existing).toDto()
        }
    }

    @Transactional(readOnly = true)
    fun getTracks(requestId: String): List<RunTrackResponseDto> =
        trackRepo.findByRequestId(requestId).map { it.toDto() }

    private fun recalcStats(entity: RunTrackEntity) {
        val pts = entity.points
        entity.pointCount = pts.size
        if (pts.size < 2) {
            entity.totalDistanceMeters = 0
            entity.totalDurationSeconds = if (pts.isEmpty()) 0 else 0
            return
        }
        var totalDist = 0.0
        var maxSpd = 0f
        for (i in 1 until pts.size) {
            totalDist += haversineMeters(pts[i - 1].lat, pts[i - 1].lng, pts[i].lat, pts[i].lng)
            pts[i].spd?.let { if (it > maxSpd) maxSpd = it }
        }
        val durationSec = ((pts.last().t - pts.first().t) / 1000).toInt().coerceAtLeast(0)
        entity.totalDistanceMeters = totalDist.toInt()
        entity.totalDurationSeconds = durationSec
        entity.maxSpeed = if (maxSpd > 0) maxSpd else null
        entity.avgPaceSeconds = if (totalDist > 0) ((durationSec / (totalDist / 1000)).toInt()) else null
    }

    private fun haversineMeters(lat1: Double, lng1: Double, lat2: Double, lng2: Double): Double {
        val r = 6371000.0
        val dLat = Math.toRadians(lat2 - lat1)
        val dLng = Math.toRadians(lng2 - lng1)
        val a = sin(dLat / 2).pow(2) + cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) * sin(dLng / 2).pow(2)
        return r * 2 * atan2(sqrt(a), sqrt(1 - a))
    }

    private fun parseRole(roleStr: String): TrackRole =
        runCatching { TrackRole.valueOf(roleStr.uppercase()) }.getOrElse {
            throw AppException(ErrorCode.INVALID_PARAM, "role 必须为 BLIND 或 VOLUNTEER")
        }

    private fun validateParticipant(userId: String, blindRunnerId: String, volunteerId: String?, role: TrackRole) {
        val ok = when (role) {
            TrackRole.BLIND     -> userId == blindRunnerId
            TrackRole.VOLUNTEER -> userId == volunteerId
        }
        if (!ok) throw AppException(ErrorCode.FORBIDDEN_ACTION, "无权上传此订单轨迹", HttpStatus.FORBIDDEN)
    }

    private fun RunTrackEntity.toDto() = RunTrackResponseDto(
        requestId = requestId,
        userId = userId,
        role = role.name,
        points = points.map { TrackPointDto(t = it.t, lat = it.lat, lng = it.lng, acc = it.acc, spd = it.spd) },
        pointCount = pointCount,
        totalDistanceMeters = totalDistanceMeters,
        totalDurationSeconds = totalDurationSeconds,
        avgPaceSeconds = avgPaceSeconds,
        maxSpeed = maxSpeed,
    )
}
