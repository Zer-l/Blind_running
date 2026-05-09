package com.guiderun.app.domain.usecase

import com.guiderun.app.domain.model.RunRequest
import com.guiderun.app.domain.repository.RunRequestRepository
import javax.inject.Inject

class CancelRunRequestUseCase @Inject constructor(
    private val runRequestRepository: RunRequestRepository,
) {
    suspend operator fun invoke(requestId: String, reason: String? = null): Result<RunRequest> =
        runRequestRepository.cancel(requestId, reason)
}
