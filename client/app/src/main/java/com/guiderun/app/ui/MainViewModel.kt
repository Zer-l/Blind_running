package com.guiderun.app.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.guiderun.app.data.local.UserPreferences
import com.guiderun.app.ui.navigation.Screen
import com.guiderun.app.util.AuthEvent
import com.guiderun.app.util.AuthEventBus
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

@HiltViewModel
class MainViewModel @Inject constructor(
    private val userPreferences: UserPreferences,
    authEventBus: AuthEventBus,
) : ViewModel() {

    val startDestination: StateFlow<String?> = flow {
        val token = userPreferences.getAccessToken()
        emit(if (token != null) Screen.Home.route else Screen.Login.route)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val authEvents: SharedFlow<AuthEvent> = authEventBus.events
}
