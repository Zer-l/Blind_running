package com.guiderun.app.domain.usecase

import com.guiderun.app.domain.model.CreateRunRequestParams
import com.guiderun.app.domain.model.GeoPoint
import com.guiderun.app.domain.model.RunRequest
import com.guiderun.app.domain.repository.RunRequestRepository
import javax.inject.Inject

class CreateRunRequestUseCase @Inject constructor(
    private val runRequestRepository: RunRequestRepository,
) {
    suspend operator fun invoke(
        durationMinutes: Int,
        location: GeoPoint,
        notes: String? = null,
    ): Result<RunRequest> = runRequestRepository.createRunRequest(
        params = CreateRunRequestParams(
            meetingLat = location.lat,
            meetingLng = location.lng,
            meetingDescription = location.description.ifBlank { "当前位置" },
            expectedDurationMinutes = durationMinutes,
            notes = notes,
        )
    )
}
