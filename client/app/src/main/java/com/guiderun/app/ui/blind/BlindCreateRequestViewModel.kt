package com.guiderun.app.ui.blind

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.guiderun.app.R
import com.guiderun.app.accessibility.HapticFeedback
import com.guiderun.app.accessibility.TtsManager
import com.guiderun.app.accessibility.voice.ParsedRequest
import com.guiderun.app.data.local.RequestPreferences
import com.guiderun.app.data.location.ForwardGeocoder
import com.guiderun.app.data.location.ReverseGeocoder
import com.guiderun.app.domain.model.GeoPoint
import com.guiderun.app.domain.repository.LocationProvider
import com.guiderun.app.domain.usecase.CreateRunRequestUseCase
import com.guiderun.app.domain.usecase.LoadLastRequestUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import timber.log.Timber
import javax.inject.Inject

/** 发起请求页 UI 状态；locationStatus 驱动页面头部图标和 TTS 定位提示。 */
data class BlindCreateRequestUiState(
    val locationStatus: LocationStatus = LocationStatus.Loading,
    val locationDescription: String = "",
    val selectedDurationMinutes: Int = 30,
    val notes: String = "",
    val isSubmitting: Boolean = false,
    val errorMessage: String? = null,
)

/** GPS 定位状态；Located 携带坐标供提交和正向地理编码偏置使用。 */
sealed interface LocationStatus {
    data object Loading : LocationStatus
    data class Located(val lat: Double, val lng: Double) : LocationStatus
    data object Failed : LocationStatus
}

/** 发起请求页导航事件；Back 区分"pop 回上一页"和"finish Activity"（起始目的地场景）。 */
sealed interface BlindCreateRequestNavEvent {
    data class ToWaitingMatch(val requestId: String) : BlindCreateRequestNavEvent
    data object Back : BlindCreateRequestNavEvent
}

/**
 * 视障端发起跑步请求页 ViewModel。
 *
 * 数据流：
 * 1. init → LoadLastRequestUseCase 预填上次偏好（本地 DataStore → 服务端最近订单兜底）
 * 2. Fragment 授权后 → startLocationUpdates → 获取 GPS → 逆地理编码为地址文本
 * 3. 用户确认（长按 2s / 语音 CONFIRM）→ submit → 若地址被手改则正向地理编码 → CreateRunRequestUseCase
 * 4. 成功 → NavEvent.ToWaitingMatch + 保存偏好供下次一键发起使用
 *
 * TTS 生命周期：onScreenResumed 时 acquire（启动 TextToSpeech）；onScreenPaused 时 release，
 * 避免与后台其他 Fragment 的 TTS 队列竞争。
 */
@HiltViewModel
class BlindCreateRequestViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val ttsManager: TtsManager,
    private val hapticFeedback: HapticFeedback,
    private val createRunRequest: CreateRunRequestUseCase,
    private val locationProvider: LocationProvider,
    private val reverseGeocoder: ReverseGeocoder,
    private val forwardGeocoder: ForwardGeocoder,
    private val requestPreferences: RequestPreferences,
    private val loadLastRequest: LoadLastRequestUseCase,
) : ViewModel() {

    private val _uiState = MutableStateFlow(BlindCreateRequestUiState())
    val uiState: StateFlow<BlindCreateRequestUiState> = _uiState.asStateFlow()

    private val _navEvent = MutableSharedFlow<BlindCreateRequestNavEvent>(replay = 0)
    val navEvent: SharedFlow<BlindCreateRequestNavEvent> = _navEvent.asSharedFlow()

    private var isPageAnnounced = false
    private var hasAnnouncedPage = false
    /** 入页播报协程：onScreenPaused 取消，避免压栈进等待匹配页后尾句 hint 抢播打断目标页。 */
    private var announceJob: kotlinx.coroutines.Job? = null

    /**
     * 系统定位完成后自动填入的地址（用于判断用户是否手改/语音改了集合点）。
     * 若 [BlindCreateRequestUiState.locationDescription] != 此值 → submit 时需触发正向地理编码。
     */
    private var autoLocationDescription: String? = null

    /**
     * 最近一次已知坐标（GPS 定位成功 / 预填偏好携带的坐标）。
     * 即使本次定位 [LocationStatus.Failed]，提交时仍用它给正向地理编码做 near 偏置，
     * 让语音/手填的裸地名能稳定落到本市附近，不再依赖"本次"实时定位是否成功。
     */
    private var lastKnownLatLng: Pair<Double, Double>? = null

    init {
        // 启动时预填上次请求参数（本地偏好优先，清数据后回退服务端最近订单）。
        viewModelScope.launch {
            loadLastRequest()?.let { prefs ->
                autoLocationDescription = prefs.locationDesc
                if (prefs.lat != null && prefs.lng != null) {
                    lastKnownLatLng = prefs.lat to prefs.lng
                }
                _uiState.update {
                    it.copy(
                        selectedDurationMinutes = prefs.durationMinutes,
                        locationDescription = prefs.locationDesc,
                        notes = prefs.notes,
                        locationStatus = if (prefs.lat != null && prefs.lng != null) {
                            LocationStatus.Located(prefs.lat, prefs.lng)
                        } else {
                            LocationStatus.Loading
                        },
                    )
                }
            }
        }
    }

    /**
     * Fragment 确认权限已授予后调用，开始获取定位。
     * 重复调用安全——已定位成功则跳过。
     */
    fun startLocationUpdates() {
        if (uiState.value.locationStatus is LocationStatus.Located) return
        loadLocation()
    }

    private fun loadLocation() {
        viewModelScope.launch {
            _uiState.update { it.copy(locationStatus = LocationStatus.Loading) }

            if (!isPageAnnounced) {
                ttsManager.speakAndWait(
                    context.getString(R.string.tts_location_loading),
                    TtsManager.Priority.NORMAL,
                )
            }

            val cached = try {
                withTimeoutOrNull(3_000) { locationProvider.getLastLocation() }
            } catch (_: Exception) {
                null
            }

            if (cached != null) {
                onLocationObtained(cached)
                return@launch
            }

            val fresh = withTimeoutOrNull(15_000) {
                locationProvider.locationUpdates(3_000).first()
            }

            if (fresh != null) {
                onLocationObtained(fresh)
            } else {
                _uiState.update { it.copy(locationStatus = LocationStatus.Failed) }
                ttsManager.speak(
                    context.getString(R.string.tts_location_timeout),
                    TtsManager.Priority.HIGH,
                )
            }
        }
    }

    private suspend fun onLocationObtained(point: GeoPoint) {
        val address = point.description.ifBlank {
            reverseGeocoder.getAddress(point.lat, point.lng)
        }
        val displayAddress = address.ifBlank { context.getString(R.string.location_default) }

        autoLocationDescription = displayAddress
        lastKnownLatLng = point.lat to point.lng
        _uiState.update {
            it.copy(
                locationStatus = LocationStatus.Located(point.lat, point.lng),
                locationDescription = displayAddress,
            )
        }

        // 播报完整请求参数（地址 + 时长 + 备注），让用户一次性听清并准备长按确认。
        // 文案片段复用语音批量回放的资源，确保两套入口（手动 / 语音）播报口径一致。
        val state = _uiState.value
        val message = buildString {
            append(context.getString(R.string.tts_location_success_prefix))
            if (address.isNotBlank()) {
                append(context.getString(R.string.blind_tts_voice_input_readback_location, address))
            }
            append(context.getString(R.string.blind_tts_voice_input_readback_duration, state.selectedDurationMinutes))
            if (state.notes.isNotBlank()) {
                append(context.getString(R.string.blind_tts_voice_input_readback_notes, state.notes))
            }
            // 语音录入引导拼进同一条 utterance 播报：入页 hint 会被本条 HIGH 播报 FLUSH，
            // 放这里确保"定位+订单信息+语音引导"一次性连续播完
            append(context.getString(R.string.blind_tts_create_voice_hint))
        }
        ttsManager.speak(message, TtsManager.Priority.HIGH)
    }

    fun onScreenResumed() {
        ttsManager.acquire()
        if (!hasAnnouncedPage) {
            hasAnnouncedPage = true
            announceJob?.cancel()
            announceJob = viewModelScope.launch {
                ttsManager.stop()
                ttsManager.speakAndWait(
                    context.getString(R.string.tts_page_create_request),
                    TtsManager.Priority.HIGH,
                )
                isPageAnnounced = true
                ttsManager.speak(
                    context.getString(R.string.tts_hint_create_request),
                    TtsManager.Priority.HIGH,
                )
            }
        }
    }

    fun onScreenPaused() {
        announceJob?.cancel()
        announceJob = null
        ttsManager.release()
    }

    fun onDurationSelected(minutes: Int) {
        _uiState.update { it.copy(selectedDurationMinutes = minutes) }
    }

    fun onNotesChanged(text: String) {
        _uiState.update { it.copy(notes = text) }
    }

    /** 用户手动编辑（或语音批量更新）了集合地点描述。 */
    fun onLocationDescriptionChanged(text: String) {
        _uiState.update { it.copy(locationDescription = text) }
    }

    /**
     * 语音批量录入回填：部分字段为空表示用户没说该项，保留当前值。
     * 若 [ParsedRequest.location] 非空 → 视作用户改写集合点（submit 时会触发 geocode）。
     */
    fun onBatchVoiceParsed(parsed: ParsedRequest) {
        _uiState.update { current ->
            current.copy(
                selectedDurationMinutes = parsed.durationMinutes ?: current.selectedDurationMinutes,
                locationDescription = parsed.location ?: current.locationDescription,
                notes = parsed.notes ?: current.notes,
            )
        }
    }

    fun onLocationPermissionDenied() {
        _uiState.update { it.copy(locationStatus = LocationStatus.Failed) }
        ttsManager.speak(
            context.getString(R.string.tts_location_permission_denied),
            TtsManager.Priority.HIGH,
        )
    }

    fun onRetryLocation() {
        _uiState.update { it.copy(locationStatus = LocationStatus.Loading) }
        loadLocation()
    }

    /** 由 BlindLongPressGestureView 倒计时结束、或语音指令 CONFIRM 触发的最终提交。 */
    fun submit() {
        viewModelScope.launch { submitRequest() }
    }

    /** HomeScreen "一键发起"路径：用上次偏好 + 当前定位（如已获取）。 */
    fun submitWithLastPrefs() {
        viewModelScope.launch {
            val prefs = loadLastRequest() ?: return@launch
            if (prefs.lat != null && prefs.lng != null) {
                lastKnownLatLng = prefs.lat to prefs.lng
            }
            _uiState.update {
                it.copy(
                    selectedDurationMinutes = prefs.durationMinutes,
                    locationDescription = prefs.locationDesc,
                    notes = prefs.notes,
                    locationStatus = if (prefs.lat != null && prefs.lng != null) {
                        LocationStatus.Located(prefs.lat, prefs.lng)
                    } else {
                        it.locationStatus
                    },
                )
            }
            submitRequest()
        }
    }

    /** 返回键退出：Fragment 已在 OnBackPressedCallback 弹对话框确认。 */
    fun onBackRequested() {
        viewModelScope.launch { _navEvent.emit(BlindCreateRequestNavEvent.Back) }
    }

    private suspend fun submitRequest() {
        val current = uiState.value
        val (originalLat, originalLng) = when (val s = current.locationStatus) {
            is LocationStatus.Located -> s.lat to s.lng
            is LocationStatus.Failed -> 0.0 to 0.0
            is LocationStatus.Loading -> {
                ttsManager.speak(
                    context.getString(R.string.tts_location_loading),
                    TtsManager.Priority.HIGH,
                )
                return
            }
        }
        _uiState.update { it.copy(isSubmitting = true, errorMessage = null) }
        ttsManager.speak(
            context.getString(R.string.tts_submitting),
            TtsManager.Priority.HIGH,
        )

        // 用户手改/语音改了地址 → 触发正向地理编码；失败则用旧 GPS 兜底
        val (lat, lng) = if (
            autoLocationDescription != null &&
            current.locationDescription != autoLocationDescription &&
            current.locationDescription.isNotBlank()
        ) {
            // 偏置中心：优先本次定位坐标，否则回退最近一次已知坐标（即使本次 Failed），
            // 把裸地名约束到本市附近，避免命中同名异地 / 解析失败回退实时坐标
            val near = ((current.locationStatus as? LocationStatus.Located)
                ?.let { it.lat to it.lng } ?: lastKnownLatLng)
                ?.let { (lat, lng) -> GeoPoint(lat, lng, "") }
            val geocoded = forwardGeocoder.geocode(current.locationDescription, near = near)
            if (geocoded != null) {
                geocoded.lat to geocoded.lng
            } else {
                ttsManager.speak(
                    context.getString(R.string.tts_edit_save_fallback),
                    TtsManager.Priority.HIGH,
                )
                originalLat to originalLng
            }
        } else {
            originalLat to originalLng
        }

        createRunRequest(
            durationMinutes = current.selectedDurationMinutes,
            location = GeoPoint(
                lat,
                lng,
                current.locationDescription.ifBlank { context.getString(R.string.location_default) },
            ),
            notes = current.notes.ifBlank { null },
        ).onSuccess { request ->
            _uiState.update { it.copy(isSubmitting = false) }
            ttsManager.speak(
                context.getString(R.string.tts_submit_success),
                TtsManager.Priority.HIGH,
            )
            hapticFeedback.confirm()
            // 保存偏好供下次 quick start 使用
            requestPreferences.saveLast(
                durationMinutes = current.selectedDurationMinutes,
                locationDesc = current.locationDescription,
                notes = current.notes,
                lat = if (lat != 0.0) lat else null,
                lng = if (lng != 0.0) lng else null,
            )
            _navEvent.emit(BlindCreateRequestNavEvent.ToWaitingMatch(request.id))
        }.onFailure { e ->
            // 不向 UI / TTS 透传原始异常信息：既避免内部细节泄露，也避免视障用户听到无意义的技术性报错
            Timber.e(e, "CreateRequestVM: submitRequest failed")
            _uiState.update {
                it.copy(
                    isSubmitting = false,
                    errorMessage = context.getString(R.string.create_request_submit_failed),
                )
            }
            ttsManager.speak(
                context.getString(R.string.tts_submit_failed_generic),
                TtsManager.Priority.HIGH,
            )
            hapticFeedback.error()
        }
    }
}
