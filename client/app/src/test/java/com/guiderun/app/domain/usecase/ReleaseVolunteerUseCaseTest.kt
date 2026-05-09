package com.guiderun.app.domain.usecase

import com.guiderun.app.domain.exception.ForbiddenActionException
import com.guiderun.app.domain.exception.ProvisioningIncompleteException
import com.guiderun.app.domain.model.RunRequestStatus
import com.guiderun.app.domain.repository.RunRequestRepository
import com.guiderun.app.util.AuthEventBus
import io.mockk.coEvery
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertTrue
import org.junit.Test

class ReleaseVolunteerUseCaseTest {

    private val repo: RunRequestRepository = mockk()
    private val authEventBus: AuthEventBus = mockk(relaxed = true)
    private val useCase = ReleaseVolunteerUseCase(repo, authEventBus)

    @Test
    fun `success returns released request`() = runTest {
        val released = fakeRequest(RunRequestStatus.MATCHING)
        coEvery { repo.releaseVolunteer("req-1") } returns Result.success(released)

        val result = useCase("req-1")

        assertTrue(result.isSuccess)
        verify(exactly = 0) { authEventBus.emitLogout() }
    }

    @Test
    fun `ProvisioningIncompleteException triggers logout and propagates failure`() = runTest {
        coEvery { repo.releaseVolunteer("req-2") } returns
            Result.failure(ProvisioningIncompleteException())

        val result = useCase("req-2")

        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() is ProvisioningIncompleteException)
        verify(exactly = 1) { authEventBus.emitLogout() }
    }

    @Test
    fun `other exceptions propagate without triggering logout`() = runTest {
        coEvery { repo.releaseVolunteer("req-3") } returns
            Result.failure(ForbiddenActionException("not your request"))

        val result = useCase("req-3")

        assertTrue(result.isFailure)
        verify(exactly = 0) { authEventBus.emitLogout() }
    }
}

private fun fakeRequest(status: RunRequestStatus) =
    com.guiderun.app.domain.model.RunRequest(
        id = "req-test", status = status, version = 1,
        blindRunner = null, volunteer = null,
        meetingLocation = com.guiderun.app.domain.model.GeoPoint(0.0, 0.0, ""),
        expectedDurationMinutes = 60,
        expectedDistanceMeters = null, expectedPaceSeconds = null,
        actualDistanceMeters = null, actualDurationSeconds = null, avgPaceSeconds = null,
        notes = null, abortReason = null, abortBy = null,
        createdAt = 0L, matchedAt = null, departedAt = null,
        metAt = null, runStartedAt = null, runEndedAt = null, closedAt = null,
    )
