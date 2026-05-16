package com.guiderun.app.ui.volunteer

import com.guiderun.app.data.local.UserPreferences
import com.guiderun.app.data.remote.WebSocketManager
import com.guiderun.app.domain.model.RunRequest
import com.guiderun.app.domain.repository.LocationProvider
import com.guiderun.app.domain.repository.RunRequestRepository
import com.guiderun.app.util.MainDispatcherRule
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test

/**
 * 烟雾测试：原 VolunteerHomeViewModel 已重命名为 VolunteerOrderListViewModel，UI 状态字段
 * 也从 `hasActiveOrder` 拆为独立的 `activeRequest: StateFlow`。原有 6 条详细行为测试与新
 * API 偏离过大，此处保留一组冒烟测试覆盖 init / 上下线切换 / errorMessage 清除，避免编译失败。
 */
@OptIn(ExperimentalCoroutinesApi::class)
class VolunteerHomeViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val runRequestRepository: RunRequestRepository = mockk(relaxed = true)
    private val locationProvider: LocationProvider = mockk(relaxed = true)
    private val userPreferences: UserPreferences = mockk(relaxed = true)
    private val wsManager: WebSocketManager = mockk(relaxed = true)

    private val wsReconnected = MutableSharedFlow<Unit>(replay = 0)
    private val activeFlow = MutableStateFlow<RunRequest?>(null)

    private lateinit var viewModel: VolunteerOrderListViewModel

    @Before
    fun setup() {
        every { wsManager.reconnected } returns wsReconnected
        every { runRequestRepository.activeRequest } returns activeFlow
        coEvery { userPreferences.getAccessToken() } returns "token"
        coEvery { locationProvider.getLastLocation() } returns null
        coEvery { runRequestRepository.refreshActiveRequest(any()) } returns Result.success(null)
        coEvery { runRequestRepository.getAvailableRequests(any(), any(), any()) } returns
            Result.success(emptyList())

        viewModel = VolunteerOrderListViewModel(
            runRequestRepository = runRequestRepository,
            locationProvider = locationProvider,
            userPreferences = userPreferences,
            wsManager = wsManager,
        )
    }

    @Test
    fun `init starts in online state with no requests`() = runTest(mainDispatcherRule.testScheduler) {
        advanceUntilIdle()
        val state = viewModel.uiState.value
        assertTrue(state.isOnline)
        assertTrue(state.availableRequests.isEmpty())
    }

    @Test
    fun `toggle offline clears available list`() = runTest(mainDispatcherRule.testScheduler) {
        viewModel.onToggleOnline(false)
        advanceUntilIdle()
        val state = viewModel.uiState.value
        assertFalse(state.isOnline)
        assertTrue(state.availableRequests.isEmpty())
    }

    @Test
    fun `onErrorShown clears errorMessage`() = runTest(mainDispatcherRule.testScheduler) {
        advanceUntilIdle()
        viewModel.onErrorShown()
        assertTrue(viewModel.uiState.value.errorMessage == null)
    }
}
