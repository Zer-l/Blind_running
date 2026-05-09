package com.guiderun.app.ui.volunteer

import androidx.lifecycle.viewModelScope
import com.guiderun.app.data.local.UserPreferences
import com.guiderun.app.data.remote.WebSocketManager
import com.guiderun.app.domain.model.GeoPoint
import com.guiderun.app.domain.model.RunRequest
import com.guiderun.app.domain.model.RunRequestStatus
import com.guiderun.app.domain.repository.LocationProvider
import com.guiderun.app.domain.repository.RunRequestRepository
import com.guiderun.app.util.MainDispatcherRule
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class VolunteerHomeViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val runRequestRepository: RunRequestRepository = mockk()
    private val locationProvider: LocationProvider = mockk()
    private val userPreferences: UserPreferences = mockk()
    private val wsManager: WebSocketManager = mockk()

    private val wsReconnected = MutableSharedFlow<Unit>(replay = 0)

    private lateinit var viewModel: VolunteerHomeViewModel

    @Before
    fun setup() {
        every { wsManager.reconnected } returns wsReconnected
        coEvery { wsManager.connect(any()) } returns Unit
        coEvery { userPreferences.getAccessToken() } returns "token"
        coEvery { locationProvider.getLastLocation() } returns null
        coEvery { runRequestRepository.getAvailableRequests(any(), any(), any()) } returns
            Result.success(emptyList())
        coEvery { runRequestRepository.getMyRequests("VOLUNTEER") } returns
            Result.success(emptyList())
        viewModel = VolunteerHomeViewModel(
            runRequestRepository = runRequestRepository,
            locationProvider = locationProvider,
            userPreferences = userPreferences,
            wsManager = wsManager,
        )
    }

    @Test
    fun `init loads available requests`() = runTest(mainDispatcherRule.testScheduler) {
        advanceUntilIdle()
        assertFalse(viewModel.uiState.value.isLoading)
    }

    @Test
    fun `toggle offline succeeds even with active order`() =
        runTest(mainDispatcherRule.testScheduler) {
            coEvery { runRequestRepository.getMyRequests("VOLUNTEER") } returns
                Result.success(listOf(fakeVolunteerRequest(RunRequestStatus.ACCEPTED)))

            viewModel.onToggleOnline(false)
            advanceUntilIdle()

            val state = viewModel.uiState.value
            assertFalse(state.isOnline)
            assertTrue(state.hasActiveOrder)
            assertNotNull(state.errorMessage)
        }

    @Test
    fun `toggle offline succeeds when no active order`() =
        runTest(mainDispatcherRule.testScheduler) {
            coEvery { runRequestRepository.getMyRequests("VOLUNTEER") } returns
                Result.success(emptyList())

            viewModel.onToggleOnline(false)
            advanceUntilIdle()

            val state = viewModel.uiState.value
            assertFalse(state.isOnline)
            assertFalse(state.hasActiveOrder)
        }

    @Test
    fun `toggle back online clears offline state and reloads`() =
        runTest(mainDispatcherRule.testScheduler) {
            coEvery { runRequestRepository.getMyRequests("VOLUNTEER") } returns
                Result.success(emptyList())
            coEvery { locationProvider.getLastLocation() } returns GeoPoint(22.91, 113.87, "test")
            viewModel.onToggleOnline(false)
            advanceUntilIdle()

            viewModel.onToggleOnline(true)
            advanceUntilIdle()

            assertTrue(viewModel.uiState.value.isOnline)
        }

    @Test
    fun `toggle online goes offline when location unavailable`() =
        runTest(mainDispatcherRule.testScheduler) {
            coEvery { runRequestRepository.getMyRequests("VOLUNTEER") } returns
                Result.success(emptyList())
            coEvery { locationProvider.getLastLocation() } returns null
            viewModel.onToggleOnline(false)
            advanceUntilIdle()

            viewModel.onToggleOnline(true)
            advanceUntilIdle()

            assertFalse(viewModel.uiState.value.isOnline)
            assertNotNull(viewModel.uiState.value.errorMessage)
        }

    @Test
    fun `error message cleared by onErrorShown`() =
        runTest(mainDispatcherRule.testScheduler) {
            coEvery { runRequestRepository.getMyRequests("VOLUNTEER") } returns
                Result.success(listOf(fakeVolunteerRequest(RunRequestStatus.EN_ROUTE)))
            viewModel.onToggleOnline(false)
            advanceUntilIdle()
            assertNotNull(viewModel.uiState.value.errorMessage)

            viewModel.onErrorShown()

            assertEquals(null, viewModel.uiState.value.errorMessage)
        }

    @After
    fun tearDown() {
        viewModel.viewModelScope.cancel()
    }
}

private fun fakeVolunteerRequest(status: RunRequestStatus) = RunRequest(
    id = "req-v1", status = status, version = 1,
    blindRunner = null, volunteer = null,
    meetingLocation = GeoPoint(0.0, 0.0, ""),
    expectedDurationMinutes = 30,
    expectedDistanceMeters = null, expectedPaceSeconds = null,
    actualDistanceMeters = null, actualDurationSeconds = null, avgPaceSeconds = null,
    notes = null, abortReason = null, abortBy = null,
    createdAt = 0L, matchedAt = null, departedAt = null,
    metAt = null, runStartedAt = null, runEndedAt = null, closedAt = null,
)
