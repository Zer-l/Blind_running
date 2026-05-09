package com.guiderun.app.ui.blind

import com.guiderun.app.accessibility.HapticFeedback
import com.guiderun.app.accessibility.TtsManager
import com.guiderun.app.data.location.ReverseGeocoder
import com.guiderun.app.domain.model.GeoPoint
import com.guiderun.app.domain.model.RunRequestStatus
import com.guiderun.app.domain.repository.LocationProvider
import com.guiderun.app.domain.usecase.CreateRunRequestUseCase
import com.guiderun.app.util.MainDispatcherRule
import io.mockk.coEvery
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class CreateRequestViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val ttsManager: TtsManager = mockk(relaxed = true)
    private val hapticFeedback: HapticFeedback = mockk(relaxed = true)
    private val createRunRequest: CreateRunRequestUseCase = mockk()
    private val locationProvider: LocationProvider = mockk()
    private val reverseGeocoder: ReverseGeocoder = mockk(relaxed = true)

    private lateinit var viewModel: CreateRequestViewModel

    @Before
    fun setup() {
        coEvery { locationProvider.getLastLocation() } returns GeoPoint(31.0, 121.0, "")
        coEvery { createRunRequest(any(), any(), any()) } returns Result.success(fakeRunRequest())
        coEvery { ttsManager.speakAndWait(any(), any()) } coAnswers { delay(1_000) }
        viewModel = CreateRequestViewModel(
            ttsManager, hapticFeedback, createRunRequest, locationProvider, reverseGeocoder,
        )
        viewModel.startLocationUpdates()
    }

    @Test
    fun `init fetches last location and sets Located status`() = runTest {
        val state = viewModel.uiState.first { it.locationStatus is LocationStatus.Located }
        val located = state.locationStatus as LocationStatus.Located
        assertEquals(31.0, located.lat, 0.001)
    }

    @Test
    fun `init sets Failed status when location unavailable`() = runTest {
        coEvery { locationProvider.getLastLocation() } returns null
        coEvery { locationProvider.locationUpdates(any()) } returns kotlinx.coroutines.flow.flow {
            kotlinx.coroutines.delay(Long.MAX_VALUE) // never emits → triggers 5s timeout
        }

        val vm = CreateRequestViewModel(
            ttsManager, hapticFeedback, createRunRequest, locationProvider, reverseGeocoder,
        )
        vm.startLocationUpdates()
        advanceTimeBy(5_001) // exhaust withTimeoutOrNull(5_000)

        val state = vm.uiState.first { it.locationStatus is LocationStatus.Failed }
        assertTrue(state.locationStatus is LocationStatus.Failed)
    }

    @Test
    fun `duration selection updates state`() {
        viewModel.onDurationSelected(90)
        assertEquals(90, viewModel.uiState.value.selectedDurationMinutes)
    }

    @Test
    fun `confirm pressed while loading speaks error instead of starting countdown`() {
        coEvery { locationProvider.getLastLocation() } returns null
        coEvery { locationProvider.locationUpdates(any()) } returns kotlinx.coroutines.flow.emptyFlow()
        val vm = CreateRequestViewModel(
            ttsManager, hapticFeedback, createRunRequest, locationProvider, reverseGeocoder,
        )
        // NOT calling startLocationUpdates() → stays in Loading

        vm.onConfirmPressed()

        assertNull(vm.uiState.value.confirmCountdown)
        verify { ttsManager.speak(any()) }
    }

    @Test
    fun `confirm pressed with location starts 3-second countdown`() = runTest {
        advanceUntilIdle() // let startLocationUpdates() from @Before complete

        viewModel.onConfirmPressed()

        val stateAfterPress = viewModel.uiState.first { it.confirmCountdown != null }
        assertNotNull(stateAfterPress.confirmCountdown)
    }

    @Test
    fun `second confirm press during countdown cancels it`() = runTest {
        advanceUntilIdle()

        viewModel.onConfirmPressed()
        viewModel.uiState.first { it.confirmCountdown != null }

        viewModel.onConfirmPressed() // cancel
        val stateCancelled = viewModel.uiState.first { it.confirmCountdown == null }
        assertNull(stateCancelled.confirmCountdown)
    }

    @Test
    fun `successful submit emits ToWaitingMatch event`() = runTest {
        advanceUntilIdle()
        val fakeRequest = fakeRunRequest()
        coEvery { createRunRequest(any(), any(), any()) } returns Result.success(fakeRequest)

        viewModel.onConfirmPressed()
        val eventDeferred = async { viewModel.navEvent.first() }
        advanceTimeBy(8_000) // 1s prompt + 3×(1s speak + 1s delay) = 7s total, +1s buffer

        val event = eventDeferred.await()
        assertTrue(event is CreateRequestNavEvent.ToWaitingMatch)
    }

    @Test
    fun `failed submit shows error message`() = runTest {
        advanceUntilIdle()
        coEvery { createRunRequest(any(), any(), any()) } returns
            Result.failure(RuntimeException("서버 오류"))

        viewModel.onConfirmPressed()
        advanceTimeBy(8_000) // 1s prompt + 3×(1s speak + 1s delay) = 7s total, +1s buffer

        val state = viewModel.uiState.first { it.errorMessage != null }
        assertNotNull(state.errorMessage)
        assertTrue(state.isSubmitting.not())
    }
}

private fun fakeRunRequest() = com.guiderun.app.domain.model.RunRequest(
    id = "r1", status = RunRequestStatus.MATCHING, version = 1,
    blindRunner = null, volunteer = null,
    meetingLocation = GeoPoint(0.0, 0.0, ""),
    expectedDurationMinutes = 60,
    expectedDistanceMeters = null, expectedPaceSeconds = null,
    actualDistanceMeters = null, actualDurationSeconds = null, avgPaceSeconds = null,
    notes = null, abortReason = null, abortBy = null,
    createdAt = 0L, matchedAt = null, departedAt = null,
    metAt = null, runStartedAt = null, runEndedAt = null, closedAt = null,
)
