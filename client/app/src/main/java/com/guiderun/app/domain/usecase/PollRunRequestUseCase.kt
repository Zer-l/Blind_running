package com.guiderun.app.domain.usecase

import com.guiderun.app.domain.model.RunRequest
import com.guiderun.app.domain.model.RunRequestStatus
import com.guiderun.app.domain.repository.RunRequestRepository
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.isActive
import javax.inject.Inject

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
                    backoffMs = minOf(backoffMs * 2, maxBackoffMs)
                }
            delay(backoffMs)
        }
    }
}
