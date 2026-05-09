package com.guiderun.app.ui.volunteer

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import com.guiderun.app.domain.exception.RequestConflictException
import com.guiderun.app.domain.model.GeoPoint
import com.guiderun.app.domain.model.RunRequest
import com.guiderun.app.domain.model.RunRequestStatus
import com.guiderun.app.domain.repository.RunRequestRepository
import com.guiderun.app.util.MainDispatcherRule
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class RequestDetailViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val runRequestRepository: RunRequestRepository = mockk()

    private lateinit var viewModel: RequestDetailViewModel

    @Before
    fun setup() {
        coEvery { runRequestRepository.getRunRequest("req-1") } returns
            Result.success(fakeDetailRequest(RunRequestStatus.MATCHING))
        viewModel = RequestDetailViewModel(
            savedStateHandle = SavedStateHandle(mapOf("requestId" to "req-1")),
            runRequestRepository = runRequestRepository,
        )
    }

    @Test
    fun `init loads request details`() = runTest(mainDispatcherRule.testScheduler) {
        advanceUntilIdle()
        assertNotNull(viewModel.uiState.value.request)
    }

    @Test
    fun `accept success emits ToNavigating event`() = runTest(mainDispatcherRule.testScheduler) {
        coEvery { runRequestRepository.accept("req-1") } returns
            Result.success(fakeDetailRequest(RunRequestStatus.ACCEPTED))

        val eventDeferred = async { viewModel.navEvent.first() }
        viewModel.onAccept()
        advanceUntilIdle()

        val event = eventDeferred.await()
        assertTrue(event is RequestDetailNavEvent.ToNavigating)
        assertEquals("req-1", (event as RequestDetailNavEvent.ToNavigating).requestId)

        viewModel.viewModelScope.cancel()
    }

    @Test
    fun `accept 409 conflict shows 手慢了 error message`() =
        runTest(mainDispatcherRule.testScheduler) {
            coEvery { runRequestRepository.accept("req-1") } returns
                Result.failure(RequestConflictException("conflict"))

            viewModel.onAccept()
            advanceUntilIdle()

            val state = viewModel.uiState.value
            assertEquals("手慢了，该订单已被接", state.errorMessage)
            assertTrue(!state.isAccepting)
        }

    @Test
    fun `accept generic failure shows retry error message`() =
        runTest(mainDispatcherRule.testScheduler) {
            coEvery { runRequestRepository.accept("req-1") } returns
                Result.failure(RuntimeException("network error"))

            viewModel.onAccept()
            advanceUntilIdle()

            val state = viewModel.uiState.value
            assertEquals("接单失败，请重试", state.errorMessage)
        }

    @Test
    fun `onErrorShown clears error message`() = runTest(mainDispatcherRule.testScheduler) {
        coEvery { runRequestRepository.accept("req-1") } returns
            Result.failure(RequestConflictException("conflict"))
        viewModel.onAccept()
        advanceUntilIdle()

        viewModel.onErrorShown()

        assertEquals(null, viewModel.uiState.value.errorMessage)
    }

    @After
    fun tearDown() {
        viewModel.viewModelScope.cancel()
    }
}

private fun fakeDetailRequest(status: RunRequestStatus) = RunRequest(
    id = "req-1", status = status, version = 1,
    blindRunner = null, volunteer = null,
    meetingLocation = GeoPoint(0.0, 0.0, ""),
    expectedDurationMinutes = 30,
    expectedDistanceMeters = null, expectedPaceSeconds = null,
    actualDistanceMeters = null, actualDurationSeconds = null, avgPaceSeconds = null,
    notes = null, abortReason = null, abortBy = null,
    createdAt = 0L, matchedAt = null, departedAt = null,
    metAt = null, runStartedAt = null, runEndedAt = null, closedAt = null,
)
