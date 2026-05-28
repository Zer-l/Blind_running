package com.guiderun.app.domain.usecase

import com.guiderun.app.data.local.LastRequestPrefs
import com.guiderun.app.data.local.RequestPreferences
import com.guiderun.app.domain.repository.RunRequestRepository
import javax.inject.Inject

/**
 * 加载"上次跑步请求"参数，供一键发起 / 发起页预填使用。
 *
 * 数据源优先级：
 * 1. 本地偏好 [RequestPreferences]（上次成功提交即写入，最快）
 * 2. 偏好为空（如清空应用数据）时回退到服务端最近一条订单
 *
 * 这样即便清空数据后重新登录，只要历史有订单，一键发起仍可用。
 */
class LoadLastRequestUseCase @Inject constructor(
    private val requestPreferences: RequestPreferences,
    private val runRequestRepository: RunRequestRepository,
) {
    suspend operator fun invoke(): LastRequestPrefs? {
        requestPreferences.loadLast()?.let { return it }
        return runRequestRepository.getMyRequests(role = "BLIND").getOrNull()
            ?.maxByOrNull { it.createdAt }
            ?.let { request ->
                LastRequestPrefs(
                    durationMinutes = request.expectedDurationMinutes,
                    locationDesc = request.meetingLocation.description,
                    notes = request.notes.orEmpty(),
                    lat = request.meetingLocation.lat.takeIf { it != 0.0 },
                    lng = request.meetingLocation.lng.takeIf { it != 0.0 },
                )
            }
    }
}
