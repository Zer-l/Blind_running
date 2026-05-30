package com.guiderun.app.data.remote

import com.guiderun.app.BuildConfig
import com.guiderun.app.data.remote.dto.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.coroutines.Job
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.min

enum class WsConnectionState { DISCONNECTED, CONNECTING, CONNECTED }

@Singleton
class WebSocketManager @Inject constructor(private val json: Json) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    // 状态变更消息（现有消费者保持不变）
    private val _messages = MutableSharedFlow<WsStatusChangeMessage>(replay = 0)
    val messages: SharedFlow<WsStatusChangeMessage> = _messages.asSharedFlow()

    // 新增：志愿者推送给视障端的实时运动数据
    private val _peerMetrics = MutableSharedFlow<WsPeerMetricsMessage>(replay = 0)
    val peerMetrics: SharedFlow<WsPeerMetricsMessage> = _peerMetrics.asSharedFlow()

    // 新增：对方提交评价后的通知
    private val _reviewReceived = MutableSharedFlow<WsReviewReceivedMessage>(replay = 0)
    val reviewReceived: SharedFlow<WsReviewReceivedMessage> = _reviewReceived.asSharedFlow()

    // 新增：紧急求助通知
    private val _emergency = MutableSharedFlow<WsEmergencyMessage>(replay = 0)
    val emergency: SharedFlow<WsEmergencyMessage> = _emergency.asSharedFlow()

    // 新增：志愿者申请结束跑步，等待视障端确认
    private val _endRunRequested = MutableSharedFlow<WsEndRunRequestedMessage>(replay = 0)
    val endRunRequested: SharedFlow<WsEndRunRequestedMessage> = _endRunRequested.asSharedFlow()

    private val _connectionState = MutableStateFlow(WsConnectionState.DISCONNECTED)
    val connectionState: StateFlow<WsConnectionState> = _connectionState.asStateFlow()

    private val _reconnected = MutableSharedFlow<Unit>(replay = 0)
    val reconnected: SharedFlow<Unit> = _reconnected.asSharedFlow()

    private var currentToken: String? = null
    private var socket: WebSocket? = null
    private var isIntentionalDisconnect = false
    private var reconnectJob: Job? = null

    private val wsBaseUrl = BuildConfig.BASE_URL
        .replace("https://", "wss://")
        .replace("http://", "ws://")
        .trimEnd('/')

    private val client = OkHttpClient.Builder()
        .pingInterval(30, TimeUnit.SECONDS)
        .build()

    fun connect(token: String) {
        // 幂等保护：同 token 且已连接/连接中时跳过，避免重复 openSocket 造成连接抖动
        if (currentToken == token && _connectionState.value != WsConnectionState.DISCONNECTED) return
        isIntentionalDisconnect = false
        currentToken = token
        openSocket(token, isReconnect = false)
    }

    fun disconnect() {
        isIntentionalDisconnect = true
        reconnectJob?.cancel()
        reconnectJob = null
        socket?.close(1000, "user disconnected")
        socket = null
        _connectionState.value = WsConnectionState.DISCONNECTED
    }

    private fun openSocket(token: String, isReconnect: Boolean) {
        _connectionState.value = WsConnectionState.CONNECTING
        val request = Request.Builder()
            .url("$wsBaseUrl/ws/v1?token=$token")
            .build()
        socket = client.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(ws: WebSocket, response: Response) {
                _connectionState.value = WsConnectionState.CONNECTED
                if (isReconnect) scope.launch { _reconnected.emit(Unit) }
            }

            override fun onMessage(ws: WebSocket, text: String) {
                runCatching { dispatchMessage(text) }
            }

            override fun onFailure(ws: WebSocket, t: Throwable, response: Response?) {
                _connectionState.value = WsConnectionState.DISCONNECTED
                scheduleReconnect()
            }

            override fun onClosed(ws: WebSocket, code: Int, reason: String) {
                _connectionState.value = WsConnectionState.DISCONNECTED
                if (!isIntentionalDisconnect) scheduleReconnect()
            }
        })
    }

    private fun dispatchMessage(text: String) {
        val element = json.parseToJsonElement(text)
        val type = element.jsonObject["type"]?.jsonPrimitive?.content ?: return
        scope.launch {
            when (type) {
                "status_changed"    -> _messages.emit(json.decodeFromString(text))
                "peer_metrics"      -> _peerMetrics.emit(json.decodeFromString(text))
                "review_received"   -> _reviewReceived.emit(json.decodeFromString(text))
                "emergency"         -> _emergency.emit(json.decodeFromString(text))
                "end_run_requested" -> _endRunRequested.emit(json.decodeFromString(text))
            }
        }
    }

    private fun scheduleReconnect() {
        if (isIntentionalDisconnect) return
        val token = currentToken ?: return
        // 取消上一个重连循环，避免多次调用（onFailure + onClosed）叠加并行重连
        reconnectJob?.cancel()
        reconnectJob = scope.launch {
            var delayMs = 1_000L
            while (!isIntentionalDisconnect) {
                delay(delayMs)
                if (_connectionState.value == WsConnectionState.CONNECTED) break
                openSocket(token, isReconnect = true)
                delayMs = min(delayMs * 2, 60_000L)
                delay(2_000)
                if (_connectionState.value == WsConnectionState.CONNECTED) break
            }
        }
    }
}
