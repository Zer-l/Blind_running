package com.guiderun.app.domain.usecase

import com.guiderun.app.domain.model.RunRequestStatus
import com.guiderun.app.domain.repository.RunRequestRepository
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class PollRunRequestUseCaseTest {

    private val repo: RunRequestRepository = mockk()
    private val useCase = PollRunRequestUseCase(repo)

    @Test
    fun `flow emits matching request then terminates on accepted`() = runTest {
        val matching = fakeRequest(RunRequestStatus.MATCHING)
        val accepted = fakeRequest(RunRequestStatus.ACCEPTED)
        var callCount = 0
        coEvery { repo.getRunRequest("req-1") } answers {
            when (callCount++) {
                0 -> Result.success(matching)
                else -> Result.success(accepted)
            }
        }

        val results = mutableListOf<com.guiderun.app.domain.model.RunRequest>()
        useCase("req-1", intervalMs = 0).collect { request ->
            results.add(request)
        }

        assertTrue("Should emit at least 2 results", results.size >= 2)
        assertEquals(RunRequestStatus.MATCHING, results[0].status)
        assertEquals(RunRequestStatus.ACCEPTED, results.last().status)
    }

    @Test
    fun `flow terminates immediately if first poll returns aborted`() = runTest {
        coEvery { repo.getRunRequest("req-2") } returns Result.success(fakeRequest(RunRequestStatus.ABORTED))

        val results = useCase("req-2", intervalMs = 0).toList()

        assertEquals(1, results.size)
        assertEquals(RunRequestStatus.ABORTED, results[0].status)
    }

    @Test
    fun `flow ignores network errors and continues polling`() = runTest {
        val matching = fakeRequest(RunRequestStatus.MATCHING)
        val accepted = fakeRequest(RunRequestStatus.ACCEPTED)
        var callCount = 0
        coEvery { repo.getRunRequest("req-3") } answers {
            when (callCount++) {
                0 -> Result.failure(RuntimeException("timeout"))
                1 -> Result.success(matching)
                else -> Result.success(accepted)
            }
        }

        val results = mutableListOf<com.guiderun.app.domain.model.RunRequest>()
        useCase("req-3", intervalMs = 0).collect { results.add(it) }

        // Error was silently skipped; first emission is MATCHING
        assertEquals(RunRequestStatus.MATCHING, results.first().status)
    }
}

private fun fakeRequest(status: RunRequestStatus) =
    com.guiderun.app.domain.model.RunRequest(
        id = "req-test",
        status = status,
        version = 1,
        blindRunner = null,
        volunteer = null,
        meetingLocation = com.guiderun.app.domain.model.GeoPoint(0.0, 0.0, ""),
        expectedDurationMinutes = 60,
        expectedDistanceMeters = null,
        expectedPaceSeconds = null,
        actualDistanceMeters = null,
        actualDurationSeconds = null,
        avgPaceSeconds = null,
        notes = null,
        abortReason = null,
        abortBy = null,
        createdAt = 0L,
        matchedAt = null,
        departedAt = null,
        metAt = null,
        runStartedAt = null,
        runEndedAt = null,
        closedAt = null,
    )
