package com.guiderun.app.ui.blind

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import com.guiderun.app.accessibility.HapticFeedback
import com.guiderun.app.accessibility.TtsManager
import com.guiderun.app.domain.exception.ForbiddenActionException
import com.guiderun.app.domain.model.GeoPoint
import com.guiderun.app.domain.model.RunRequestStatus
import com.guiderun.app.domain.model.UserSummary
import com.guiderun.app.domain.repository.RunRequestRepository
import com.guiderun.app.domain.usecase.ReleaseVolunteerUseCase
import com.guiderun.app.util.MainDispatcherRule
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class MatchedViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val ttsManager: TtsManager = mockk(relaxed = true)
    private val hapticFeedback: HapticFeedback = mockk(relaxed = true)
    private val runRequestRepository: RunRequestRepository = mockk()
    private val releaseVolunteer: ReleaseVolunteerUseCase = mockk()

    private lateinit var viewModel: MatchedViewModel

    @Before
    fun setup() {
        // speakAndWait simulates 1s TTS duration so countdown states are observable
        coEvery { ttsManager.speakAndWait(any(), any()) } coAnswers { delay(1_000) }
        coEvery { runRequestRepository.getRunRequest("req-1") } returnsMany listOf(
            Result.success(fakeRequest(RunRequestStatus.ACCEPTED)),
            Result.success(fakeRequest(RunRequestStatus.RUNNING)),  // 第二次后退出轮询
        )
        coEvery { runRequestRepository.confirmMet("req-1") } returns
                Result.success(fakeRequest(RunRequestStatus.MET))
        coEvery { runRequestRepository.startRun("req-1") } returns
                Result.success(fakeRequest(RunRequestStatus.RUNNING))
        coEvery { releaseVolunteer("req-1") } returns
                Result.success(fakeRequest(RunRequestStatus.MATCHING))
        viewModel = MatchedViewModel(
            savedStateHandle = SavedStateHandle(mapOf("requestId" to "req-1")),
            ttsManager = ttsManager,
            hapticFeedback = hapticFeedback,
            runRequestRepository = runRequestRepository,
            releaseVolunteer = releaseVolunteer,
        )
    }

    @Test
    fun `init announces full volunteer info on first poll`() =
        runTest(mainDispatcherRule.testScheduler) {
            val volunteer = fakeVolunteer("张三", rating = 4.8f, totalRuns = 15)
            coEvery { runRequestRepository.getRunRequest("req-vol") } returns
                    Result.success(fakeRequest(RunRequestStatus.ACCEPTED, volunteer))
            val vm = MatchedViewModel(
                savedStateHandle = SavedStateHandle(mapOf("requestId" to "req-vol")),
                ttsManager = ttsManager, hapticFeedback = hapticFeedback,
                runRequestRepository = runRequestRepository, releaseVolunteer = releaseVolunteer,
            )

            advanceTimeBy(1) // trigger first poll (before the 5s delay)

            verify {
                ttsManager.speak(
                    match { it.contains("张三") && it.contains("15") },
                    TtsManager.Priority.HIGH
                )
            }
            vm.viewModelScope.cancel()
            viewModel.viewModelScope.cancel()
        }

    @Test
    fun `volunteer announcement fires only once across multiple polls`() =
        runTest(mainDispatcherRule.testScheduler) {
            val volunteer = fakeVolunteer("李四", rating = null, totalRuns = 3)
            coEvery { runRequestRepository.getRunRequest("req-once") } returns
                    Result.success(fakeRequest(RunRequestStatus.ACCEPTED, volunteer))
            val vm = MatchedViewModel(
                savedStateHandle = SavedStateHandle(mapOf("requestId" to "req-once")),
                ttsManager = ttsManager, hapticFeedback = hapticFeedback,
                runRequestRepository = runRequestRepository, releaseVolunteer = releaseVolunteer,
            )

            advanceTimeBy(1)    // first poll
            advanceTimeBy(5_000) // second poll (5s delay)

            // Should only have spoken the volunteer announcement once
            verify(exactly = 1) {
                ttsManager.speak(match { it.contains("李四") }, TtsManager.Priority.HIGH)
            }
            vm.viewModelScope.cancel()
            viewModel.viewModelScope.cancel()
        }

    @Test
    fun `release pressed starts 5-second countdown`() =
        runTest(mainDispatcherRule.testScheduler) {
            viewModel.onReleasePressed()

            val state = viewModel.uiState.first { it.releaseCountdown != null }
            assertNotNull(state.releaseCountdown)

            viewModel.viewModelScope.cancel()
        }

    @Test
    fun `tryHandleShakeCancel cancels release countdown`() =
        runTest(mainDispatcherRule.testScheduler) {
            viewModel.onReleasePressed()
            viewModel.uiState.first { it.releaseCountdown != null }

            val consumed = viewModel.tryHandleShakeCancel()

            assertTrue(consumed)
            val state = viewModel.uiState.first { it.releaseCountdown == null }
            assertNull(state.releaseCountdown)

            viewModel.viewModelScope.cancel()
        }

    @Test
    fun `tryHandleShakeCancel returns false when no action is active`() {
        assertFalse(viewModel.tryHandleShakeCancel())
    }

    @Test
    fun `release countdown emits ToWaiting on success`() =
        runTest(mainDispatcherRule.testScheduler) {
            // Override getRunRequest to always return ACCEPTED (not RUNNING) during countdown
            coEvery { runRequestRepository.getRunRequest("req-1") } returns
                    Result.success(fakeRequest(RunRequestStatus.ACCEPTED))
            coEvery { releaseVolunteer("req-1") } returns
                    Result.success(fakeRequest(RunRequestStatus.MATCHING))

            viewModel.onReleasePressed()
            // Subscribe before advancing time: replay=0 means emit suspends until subscriber exists
            val eventDeferred = async { viewModel.navEvent.first() }
            advanceTimeBy(7_000) // prompt(1s) + 5×countdown(1s each) = 6s, +1s buffer

            val event = eventDeferred.await()
            assertTrue(event is MatchedNavEvent.ToWaiting)

            viewModel.viewModelScope.cancel()
        }

    @Test
    fun `release failure speaks error message`() =
        runTest(mainDispatcherRule.testScheduler) {
            // Override getRunRequest to always return ACCEPTED (not RUNNING) during countdown
            coEvery { runRequestRepository.getRunRequest("req-1") } returns
                    Result.success(fakeRequest(RunRequestStatus.ACCEPTED))
            coEvery { releaseVolunteer("req-1") } returns
                    Result.failure(ForbiddenActionException("not your request"))

            viewModel.onReleasePressed()
            advanceTimeBy(7_000)

            verify { ttsManager.speak(match { it.contains("更换失败") }, any()) }
            viewModel.viewModelScope.cancel()
        }

    @Test
    fun `aborted status from polling emits ToHome`() =
        runTest(mainDispatcherRule.testScheduler) {
            coEvery { runRequestRepository.getRunRequest("req-abort") } returns
                    Result.success(fakeRequest(RunRequestStatus.ABORTED))
            val vm = MatchedViewModel(
                savedStateHandle = SavedStateHandle(mapOf("requestId" to "req-abort")),
                ttsManager = ttsManager, hapticFeedback = hapticFeedback,
                runRequestRepository = runRequestRepository, releaseVolunteer = releaseVolunteer,
            )

            val event = vm.navEvent.first()
            assertTrue(event is MatchedNavEvent.ToHome)

            vm.viewModelScope.cancel()
            viewModel.viewModelScope.cancel()
        }

    @Test
    fun `short press speaks gesture hint`() {
        viewModel.onShortPress()
        verify { ttsManager.speak(any(), any()) }
    }

    @Test
    fun `confirm accept speaks success and triggers haptic`() =
        runTest(mainDispatcherRule.testScheduler) {
            viewModel.onConfirmAccept()
            advanceUntilIdle()
            verify { hapticFeedback.confirm() }
            coVerify { runRequestRepository.startRun("req-1") }
            verify { ttsManager.speak(match { it.contains("跑步开始") }, TtsManager.Priority.HIGH) }
        }

    @After
    fun tearDown() {
        if (::viewModel.isInitialized) {
            viewModel.viewModelScope.cancel()
        }
    }
}

private fun fakeVolunteer(
    nickname: String,
    rating: Float? = 4.5f,
    totalRuns: Int = 10,
) = UserSummary(
    id = "v1", nickname = nickname, avatarUrl = null, gender = null,
    rating = rating, totalRuns = totalRuns,
)

private fun fakeRequest(
    status: RunRequestStatus,
    volunteer: UserSummary? = null,
) = com.guiderun.app.domain.model.RunRequest(
    id = "req-test", status = status, version = 1,
    blindRunner = null, volunteer = volunteer,
    meetingLocation = GeoPoint(0.0, 0.0, ""),
    expectedDurationMinutes = 60,
    expectedDistanceMeters = null, expectedPaceSeconds = null,
    actualDistanceMeters = null, actualDurationSeconds = null, avgPaceSeconds = null,
    notes = null, abortReason = null, abortBy = null,
    createdAt = 0L, matchedAt = null, departedAt = null,
    metAt = null, runStartedAt = null, runEndedAt = null, closedAt = null,
)
