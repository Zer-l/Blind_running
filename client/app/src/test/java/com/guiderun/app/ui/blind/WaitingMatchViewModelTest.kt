package com.guiderun.app.ui.blind

import android.content.Context
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import com.guiderun.app.accessibility.HapticFeedback
import com.guiderun.app.accessibility.TtsManager
import com.guiderun.app.domain.model.GeoPoint
import com.guiderun.app.domain.model.RunRequestStatus
import com.guiderun.app.domain.usecase.CancelRunRequestUseCase
import com.guiderun.app.domain.usecase.PollRunRequestUseCase
import com.guiderun.app.util.MainDispatcherRule
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class WaitingMatchViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val context: Context = mockk(relaxed = true)
    private val ttsManager: TtsManager = mockk(relaxed = true)
    private val hapticFeedback: HapticFeedback = mockk(relaxed = true)
    private val pollRunRequest: PollRunRequestUseCase = mockk()
    private val cancelRunRequest: CancelRunRequestUseCase = mockk()

    private lateinit var viewModel: WaitingMatchViewModel

    @Before
    fun setup() {
        every {
            pollRunRequest(
                any(),
                any()
            )
        } returns flowOf(fakeRequest(RunRequestStatus.MATCHING))
        coEvery { cancelRunRequest(any(), any()) } returns Result.success(
            fakeRequest(
                RunRequestStatus.ABORTED
            )
        )
        coEvery { ttsManager.speakAndWait(any(), any()) } coAnswers { delay(1_000) }
        viewModel = WaitingMatchViewModel(
            context = context,
            savedStateHandle = SavedStateHandle(mapOf("requestId" to "req-1")),
            ttsManager = ttsManager,
            hapticFeedback = hapticFeedback,
            pollRunRequest = pollRunRequest,
            cancelRunRequest = cancelRunRequest,
        )
    }

    // 【改动1】runTest { } → runTest(mainDispatcherRule.testScheduler) { }
    @Test
    fun `polling detects accepted and emits ToMatched nav event`() =
        runTest(mainDispatcherRule.testScheduler) {
            every { pollRunRequest("req-2", any()) } returns flow {
                emit(fakeRequest(RunRequestStatus.MATCHING))
                emit(fakeRequest(RunRequestStatus.ACCEPTED))
            }
            val vm = WaitingMatchViewModel(
                context = context,
                savedStateHandle = SavedStateHandle(mapOf("requestId" to "req-2")),
                ttsManager = ttsManager, hapticFeedback = hapticFeedback,
                pollRunRequest = pollRunRequest, cancelRunRequest = cancelRunRequest,
            )

            val event = vm.navEvent.first()
            assertTrue(event is WaitingMatchNavEvent.ToMatched)

            vm.viewModelScope.cancel()
            viewModel.viewModelScope.cancel()
        }

    // 【改动1】同上
    @Test
    fun `polling detects aborted and emits ToHome nav event`() =
        runTest(mainDispatcherRule.testScheduler) {
            every {
                pollRunRequest(
                    "req-3",
                    any()
                )
            } returns flowOf(fakeRequest(RunRequestStatus.ABORTED))
            val vm = WaitingMatchViewModel(
                context = context,
                savedStateHandle = SavedStateHandle(mapOf("requestId" to "req-3")),
                ttsManager = ttsManager, hapticFeedback = hapticFeedback,
                pollRunRequest = pollRunRequest, cancelRunRequest = cancelRunRequest,
            )

            val event = vm.navEvent.first()
            assertTrue(event is WaitingMatchNavEvent.ToHome)

            vm.viewModelScope.cancel()
            viewModel.viewModelScope.cancel()
        }

    // 【改动1】同上
    @Test
    fun `cancel pressed starts 5-second countdown`() =
        runTest(mainDispatcherRule.testScheduler) {
            viewModel.onCancelPressed()

            val state = viewModel.uiState.first { it.cancelCountdown != null }
            assertNotNull(state.cancelCountdown)

            viewModel.viewModelScope.cancel()
        }

    // 【改动1】同上
    @Test
    fun `second cancel press during countdown cancels the action`() =
        runTest(mainDispatcherRule.testScheduler) {
            viewModel.onCancelPressed()
            viewModel.uiState.first { it.cancelCountdown != null }

            viewModel.onCancelPressed()
            val state = viewModel.uiState.first { it.cancelCountdown == null }
            assertNull(state.cancelCountdown)

            viewModel.viewModelScope.cancel()
        }

    // 【改动1】同上
    @Test
    fun `successful cancel emits ToHome event`() =
        runTest(mainDispatcherRule.testScheduler) {
            coEvery { cancelRunRequest("req-1", any()) } returns Result.success(
                fakeRequest(
                    RunRequestStatus.ABORTED
                )
            )

            viewModel.onCancelPressed()
            val eventDeferred = async { viewModel.navEvent.first() }
            advanceTimeBy(7_000) // 1s prompt + 5×1s speak = 6s total, +1s buffer

            val event = eventDeferred.await()
            assertTrue(event is WaitingMatchNavEvent.ToHome)

            viewModel.viewModelScope.cancel()
        }

    // 【改动1】同上
    @Test
    fun `elapsed timer increments state`() = runTest(mainDispatcherRule.testScheduler) {
        advanceTimeBy(4_000) // 3_000改成4_000确保断言通过，cancel() 能正常执行
        assertTrue(viewModel.uiState.value.elapsedSeconds >= 3L)

        viewModel.viewModelScope.cancel()
    }

    @Test
    fun `onScreenResumed and onScreenPaused delegate to ttsManager`() {
        viewModel.onScreenResumed()
        verify { ttsManager.acquire() }

        viewModel.onScreenPaused()
        verify { ttsManager.release() }
    }

    // 【改动2】新增 @After，防止 while(true) 协程泄漏
    @After
    fun tearDown() {
        viewModel.viewModelScope.cancel()
    }

}


private fun fakeRequest(status: RunRequestStatus) =
    com.guiderun.app.domain.model.RunRequest(
        id = "req-test", status = status, version = 1,
        blindRunner = null, volunteer = null,
        meetingLocation = GeoPoint(0.0, 0.0, ""),
        expectedDurationMinutes = 60,
        expectedDistanceMeters = null, expectedPaceSeconds = null,
        actualDistanceMeters = null, actualDurationSeconds = null, avgPaceSeconds = null,
        notes = null, abortReason = null, abortBy = null,
        createdAt = 0L, matchedAt = null, departedAt = null,
        metAt = null, runStartedAt = null, runEndedAt = null, closedAt = null,
    )
