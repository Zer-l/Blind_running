package com.guiderun.app.domain.usecase

import com.guiderun.app.domain.model.RunRequest
import com.guiderun.app.domain.model.RunRequestStatus
import com.guiderun.app.domain.repository.RunRequestRepository
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.isActive
import timber.log.Timber
import javax.inject.Inject

/**
 * 视障端等待匹配阶段的轮询 UseCase。
 *
 * 返回冷 Flow：每次 collect 独立一条轮询循环。
 * 正常流程依赖 WS 推送快速感知状态变化；本 UseCase 作为 WS 异常时的兜底（断网重连前的保底拉取），
 * 默认 5s 轮询一次，失败时指数退避（最大 60s）避免频繁重试消耗电量。
 * MATCHING 状态结束（ACCEPTED / ABORTED / 其他终态）时自动终止 Flow。
 */
class PollRunRequestUseCase @Inject constructor(
    private val runRequestRepository: RunRequestRepository,
) {
    /**
     * Polls the given request every [intervalMs] milliseconds and emits each updated state.
     * Terminates automatically when the request leaves MATCHING state (accepted, aborted, etc.).
     * On consecutive failures applies exponential backoff, capped at [maxBackoffMs].
     */
    operator fun invoke(
        requestId: String,
        intervalMs: Long = 5_000,
        maxBackoffMs: Long = 60_000,
    ): Flow<RunRequest> = flow {
        var backoffMs = intervalMs
        while (currentCoroutineContext().isActive) {
            runRequestRepository.getRunRequest(requestId)
                .onSuccess { request ->
                    backoffMs = intervalMs
                    emit(request)
                    if (request.status != RunRequestStatus.MATCHING) return@flow
                }
                .onFailure {
                    Timber.w(it, "PollRunRequest: poll failed, backoff=%dms", backoffMs)
                    backoffMs = minOf(backoffMs * 2, maxBackoffMs)
                }
            delay(backoffMs)
        }
    }
}
