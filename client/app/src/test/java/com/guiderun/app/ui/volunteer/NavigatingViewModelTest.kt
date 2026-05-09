package com.guiderun.app.ui.volunteer

import android.app.Application
import android.content.Intent
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import com.guiderun.app.accessibility.TtsManager
import com.guiderun.app.data.remote.WebSocketManager
import com.guiderun.app.data.remote.dto.WsStatusChangeMessage
import com.guiderun.app.domain.model.GeoPoint
import com.guiderun.app.domain.model.RunRequest
import com.guiderun.app.domain.model.RunRequestStatus
import com.guiderun.app.domain.repository.LocationProvider
import com.guiderun.app.domain.repository.RunRequestRepository
import com.guiderun.app.util.MainDispatcherRule
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkConstructor
import io.mockk.unmockkConstructor
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class NavigatingViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val application: Application = mockk(relaxed = true)
    private val ttsManager: TtsManager = mockk(relaxed = true)
    private val runRequestRepository: RunRequestRepository = mockk()
    private val locationProvider: LocationProvider = mockk()
    private val wsManager: WebSocketManager = mockk()

    private val wsMessages = MutableSharedFlow<WsStatusChangeMessage>(replay = 0)
    private val wsReconnected = MutableSharedFlow<Unit>(replay = 0)

    private lateinit var viewModel: NavigatingViewModel

    @Before
    fun setup() {
        // ===== mock 掉 Intent 构造函数，解决 putExtra 未 mock 的问题 =====
        mockkConstructor(Intent::class)
        every { anyConstructed<Intent>().putExtra(any<String>(), any<String>()) } returns mockk(relaxed = true)
        every { anyConstructed<Intent>().putExtra(any<String>(), any<Int>()) } returns mockk(relaxed = true)
        every { anyConstructed<Intent>().putExtra(any<String>(), any<Boolean>()) } returns mockk(relaxed = true)
        every { anyConstructed<Intent>().setAction(any()) } returns mockk(relaxed = true)
        every { anyConstructed<Intent>().setPackage(any()) } returns mockk(relaxed = true)
        every { anyConstructed<Intent>().setClass(any(), any<Class<*>>()) } returns mockk(relaxed = true)
        // ================================================================

        every { wsManager.messages } returns wsMessages
        every { wsManager.reconnected } returns wsReconnected
        every { locationProvider.locationUpdates(any()) } returns emptyFlow()
        coEvery { runRequestRepository.getRunRequest("req-1") } returns
                Result.success(fakeNavigatingRequest(RunRequestStatus.EN_ROUTE))
        coEvery { ttsManager.speakAndWait(any(), any()) } returns Unit

        viewModel = NavigatingViewModel(
            application = application,
            savedStateHandle = SavedStateHandle(mapOf("requestId" to "req-1")),
            ttsManager = ttsManager,
            runRequestRepository = runRequestRepository,
            locationProvider = locationProvider,
            wsManager = wsManager,
        )
    }

    @Test
    fun `WS ABORTED triggered by BLIND speaks cancellation message and navigates home`() =
        runTest(mainDispatcherRule.testScheduler) {
            val eventDeferred = async { viewModel.navEvent.first() }
            wsMessages.emit(WsStatusChangeMessage(
                type = "status_changed", requestId = "req-1",
                toStatus = RunRequestStatus.ABORTED.name, version = 2,
                triggeredRole = "BLIND",
            ))
            advanceUntilIdle()

            val event = eventDeferred.await()
            assertTrue(event is NavigatingNavEvent.ToHome)
            coVerify {
                ttsManager.speakAndWait(
                    match { it.contains("视障用户") },
                    TtsManager.Priority.HIGH,
                )
            }

            viewModel.viewModelScope.cancel()
        }

    @Test
    fun `WS ABORTED triggered by VOLUNTEER speaks abandon message and navigates home`() =
        runTest(mainDispatcherRule.testScheduler) {
            val eventDeferred = async { viewModel.navEvent.first() }
            wsMessages.emit(WsStatusChangeMessage(
                type = "status_changed", requestId = "req-1",
                toStatus = RunRequestStatus.ABORTED.name, version = 2,
                triggeredRole = "VOLUNTEER",
            ))
            advanceUntilIdle()

            val event = eventDeferred.await()
            assertTrue(event is NavigatingNavEvent.ToHome)
            coVerify {
                ttsManager.speakAndWait(
                    match { it.contains("放弃") },
                    TtsManager.Priority.HIGH,
                )
            }

            viewModel.viewModelScope.cancel()
        }

    @Test
    fun `WS MET emits ToMet nav event`() =
        runTest(mainDispatcherRule.testScheduler) {
            val eventDeferred = async { viewModel.navEvent.first() }
            wsMessages.emit(WsStatusChangeMessage(
                type = "status_changed", requestId = "req-1",
                toStatus = RunRequestStatus.MET.name, version = 2,
            ))
            advanceUntilIdle()

            val event = eventDeferred.await()
            assertTrue(event is NavigatingNavEvent.ToMet)

            viewModel.viewModelScope.cancel()
        }

    @Test
    fun `WS message for different requestId is ignored`() =
        runTest(mainDispatcherRule.testScheduler) {
            wsMessages.emit(WsStatusChangeMessage(
                type = "status_changed", requestId = "other-req",
                toStatus = RunRequestStatus.ABORTED.name, version = 2,
            ))
            advanceUntilIdle()

            coVerify(exactly = 0) { ttsManager.speakAndWait(any(), any()) }

            viewModel.viewModelScope.cancel()
        }

    @After
    fun tearDown() {
        unmockkConstructor(Intent::class)  // 必须清理，否则影响其他测试类
        viewModel.viewModelScope.cancel()
    }
}

private fun fakeNavigatingRequest(status: RunRequestStatus) = RunRequest(
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
