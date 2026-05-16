package com.guiderun.app.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.guiderun.app.data.local.UserPreferences
import com.guiderun.app.domain.model.RunRequest
import com.guiderun.app.domain.model.UserRole
import com.guiderun.app.domain.repository.RunRequestRepository
import com.guiderun.app.ui.navigation.Screen
import com.guiderun.app.util.AuthEvent
import com.guiderun.app.util.AuthEventBus
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class MainViewModel @Inject constructor(
    private val userPreferences: UserPreferences,
    private val runRequestRepository: RunRequestRepository,
    authEventBus: AuthEventBus,
) : ViewModel() {

    val themeId: StateFlow<String> = userPreferences.observeThemeId()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), "orange")

    val startDestination: StateFlow<String?> = flow {
        val token = userPreferences.getAccessToken()
        emit(if (token != null) Screen.Home.route else Screen.Login.route)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val authEvents: SharedFlow<AuthEvent> = authEventBus.events

    /** 当前 ACTIVE_ROLE 的快照，供路由时同步读取。冷启动后由 init 块填充。 */
    @Volatile
    private var cachedRole: UserRole? = null

    init {
        viewModelScope.launch {
            cachedRole = userPreferences.getActiveRole()
                ?.let { runCatching { UserRole.valueOf(it) }.getOrNull() }
            // 冷启动只刷新 Repository.activeRequest（让首页横幅有数据展示），不主动跳转流程页。
            // 用户从首页横幅点击 onResumeActiveOrder 才会路由，符合主流 App 订单中心体验。
            refreshActiveRequest()
        }
    }

    suspend fun refreshActiveRequest() {
        val activeRole = userPreferences.getActiveRole() ?: return
        val serverRole = when (activeRole) {
            "BLIND_RUNNER" -> "BLIND"
            "VOLUNTEER"    -> "VOLUNTEER"
            else           -> return
        }
        runRequestRepository.refreshActiveRequest(serverRole)
    }

    fun activeRequestNow(): RunRequest? = runRequestRepository.activeRequest.value

    fun currentRole(): UserRole? = cachedRole
}
