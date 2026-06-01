package com.guiderun.app.domain.usecase

import com.guiderun.app.domain.model.RunRequest
import com.guiderun.app.domain.repository.RunRequestRepository
import javax.inject.Inject

/**
 * 取消跑步请求 UseCase。
 *
 * 视障端唯一的"主动退出"路径（删除"更换志愿者"后）。
 * reason 可选，服务端记录取消原因（用于数据分析 / 防刷机制）。
 */
class CancelRunRequestUseCase @Inject constructor(
    private val runRequestRepository: RunRequestRepository,
) {
    suspend operator fun invoke(requestId: String, reason: String? = null): Result<RunRequest> =
        runRequestRepository.cancel(requestId, reason)
}
