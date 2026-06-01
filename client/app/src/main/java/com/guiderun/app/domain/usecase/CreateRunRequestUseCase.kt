package com.guiderun.app.domain.usecase

import com.guiderun.app.domain.model.CreateRunRequestParams
import com.guiderun.app.domain.model.GeoPoint
import com.guiderun.app.domain.model.RunRequest
import com.guiderun.app.domain.repository.RunRequestRepository
import javax.inject.Inject

/**
 * 创建跑步请求 UseCase。
 *
 * Domain 层不引用 Android 资源（Context），地址描述的兜底文案由 ViewModel（UI 层）保证非空后传入。
 * 参数封装在 CreateRunRequestParams，避免直接把 ViewModel 的 UI 状态传到 Repository。
 */
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
            // 描述兜底由 UI 层（持有资源）保证非空，domain 层不引用 Android 资源
            meetingDescription = location.description,
            expectedDurationMinutes = durationMinutes,
            notes = notes,
        )
    )
}
