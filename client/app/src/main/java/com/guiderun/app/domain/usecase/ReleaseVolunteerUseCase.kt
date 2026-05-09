package com.guiderun.app.domain.usecase

import com.guiderun.app.domain.exception.ProvisioningIncompleteException
import com.guiderun.app.domain.model.RunRequest
import com.guiderun.app.domain.repository.RunRequestRepository
import com.guiderun.app.util.AuthEventBus
import javax.inject.Inject

class ReleaseVolunteerUseCase @Inject constructor(
    private val runRequestRepository: RunRequestRepository,
    private val authEventBus: AuthEventBus,
) {
    suspend operator fun invoke(requestId: String): Result<RunRequest> =
        runRequestRepository.releaseVolunteer(requestId)
            .onFailure { e ->
                // Defensive: if provisioning is incomplete the session is invalid — force re-login
                if (e is ProvisioningIncompleteException) authEventBus.emitLogout()
            }
}
