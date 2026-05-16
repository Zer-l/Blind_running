package com.guiderun.app.ui.blind

import android.content.Context
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import com.guiderun.app.R
import com.guiderun.app.accessibility.HapticFeedback
import com.guiderun.app.accessibility.TtsManager
import com.guiderun.app.domain.model.GeoPoint
import com.guiderun.app.domain.model.RunRequestStatus
import com.guiderun.app.domain.model.UserSummary
import com.guiderun.app.domain.repository.RunRequestRepository
import com.guiderun.app.util.MainDispatcherRule
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class MatchedViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val context: Context = mockk(relaxed = true)
    private val ttsManager: TtsManager = mockk(relaxed = true)
    private val hapticFeedback: HapticFeedback = mockk(relaxed = true)
    private val runRequestRepository: RunRequestRepository = mockk()

    private lateinit var viewModel: MatchedViewModel

    @Before
    fun setup() {
        // relaxed Context mock 默认 getString 返回空串，单测里需要的几个 TTS 文案显式注入
        io.mockk.every { context.getString(R.string.tts_start_running) } returns "正在开始跑步"
        io.mockk.every { context.getString(R.string.tts_running_started) } returns "跑步开始，正在录制轨迹"
        coEvery { ttsManager.speakAndWait(any(), any()) } coAnswers { delay(1_000) }
        coEvery { runRequestRepository.getRunRequest("req-1") } returnsMany listOf(
            Result.success(fakeRequest(RunRequestStatus.ACCEPTED)),
            Result.success(fakeRequest(RunRequestStatus.RUNNING)),  // 第二次后退出轮询
        )
        coEvery { runRequestRepository.confirmMet("req-1") } returns
                Result.success(fakeRequest(RunRequestStatus.MET))
        coEvery { runRequestRepository.startRun("req-1") } returns
                Result.success(fakeRequest(RunRequestStatus.RUNNING))
        viewModel = MatchedViewModel(
            context = context,
            savedStateHandle = SavedStateHandle(mapOf("requestId" to "req-1")),
            ttsManager = ttsManager,
            hapticFeedback = hapticFeedback,
            runRequestRepository = runRequestRepository,
        )
    }

    @Test
    fun `init announces full volunteer info on first poll`() =
        runTest(mainDispatcherRule.testScheduler) {
            val volunteer = fakeVolunteer("张三", rating = 4.8f, totalRuns = 15)
            coEvery { runRequestRepository.getRunRequest("req-vol") } returns
                    Result.success(fakeRequest(RunRequestStatus.ACCEPTED, volunteer))
            val vm = MatchedViewModel(
                context = context,
                savedStateHandle = SavedStateHandle(mapOf("requestId" to "req-vol")),
                ttsManager = ttsManager, hapticFeedback = hapticFeedback,
                runRequestRepository = runRequestRepository,
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
                context = context,
                savedStateHandle = SavedStateHandle(mapOf("requestId" to "req-once")),
                ttsManager = ttsManager, hapticFeedback = hapticFeedback,
                runRequestRepository = runRequestRepository,
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
    fun `aborted status from polling emits ToHome`() =
        runTest(mainDispatcherRule.testScheduler) {
            coEvery { runRequestRepository.getRunRequest("req-abort") } returns
                    Result.success(fakeRequest(RunRequestStatus.ABORTED))
            val vm = MatchedViewModel(
                context = context,
                savedStateHandle = SavedStateHandle(mapOf("requestId" to "req-abort")),
                ttsManager = ttsManager, hapticFeedback = hapticFeedback,
                runRequestRepository = runRequestRepository,
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
    fun `confirm pressed in MET state starts countdown and runs startRun after`() =
        runTest(mainDispatcherRule.testScheduler) {
            coEvery { runRequestRepository.getRunRequest("req-met") } returns
                    Result.success(fakeRequest(RunRequestStatus.MET))
            val vm = MatchedViewModel(
                context = context,
                savedStateHandle = SavedStateHandle(mapOf("requestId" to "req-met")),
                ttsManager = ttsManager, hapticFeedback = hapticFeedback,
                runRequestRepository = runRequestRepository,
            )
            coEvery { runRequestRepository.startRun("req-met") } returns
                    Result.success(fakeRequest(RunRequestStatus.RUNNING))
            advanceTimeBy(1) // 首次 poll → currentStatus = MET

            vm.onConfirmMetPressed()
            advanceTimeBy(8_000) // prompt(1s) + 5x(1s 数字) = 6s，+ 缓冲
            advanceUntilIdle()

            coVerify { runRequestRepository.startRun("req-met") }
            verify { hapticFeedback.warning() } // 启动倒计时时的 warning
            vm.viewModelScope.cancel()
        }

    @Test
    fun `second press during confirm countdown cancels it`() =
        runTest(mainDispatcherRule.testScheduler) {
            coEvery { runRequestRepository.getRunRequest("req-c") } returns
                    Result.success(fakeRequest(RunRequestStatus.MET))
            val vm = MatchedViewModel(
                context = context,
                savedStateHandle = SavedStateHandle(mapOf("requestId" to "req-c")),
                ttsManager = ttsManager, hapticFeedback = hapticFeedback,
                runRequestRepository = runRequestRepository,
            )
            advanceTimeBy(1)
            vm.onConfirmMetPressed() // 启动倒计时
            advanceTimeBy(1_500) // 倒计时进行中
            vm.onConfirmMetPressed() // 撤销
            advanceUntilIdle()

            assertTrue(vm.uiState.value.confirmCountdown == null)
            coVerify(exactly = 0) { runRequestRepository.startRun(any()) }
            vm.viewModelScope.cancel()
        }

    @Test
    fun `confirm pressed in ACCEPTED state speaks waiting hint and skips startRun`() =
        runTest(mainDispatcherRule.testScheduler) {
            io.mockk.every { context.getString(R.string.tts_waiting_volunteer_depart) } returns "志愿者已接单，正在准备出发，请稍候"
            // viewModel(@Before) 走 req-1 ACCEPTED 路径
            advanceTimeBy(1) // 首次 poll → currentStatus = ACCEPTED

            viewModel.onConfirmMetPressed()
            advanceUntilIdle()

            coVerify(exactly = 0) { runRequestRepository.startRun(any()) }
            verify { hapticFeedback.warning() }
            verify { ttsManager.speak(match { it.contains("准备出发") }, TtsManager.Priority.HIGH) }
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
