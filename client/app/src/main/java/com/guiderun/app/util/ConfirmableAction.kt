package com.guiderun.app.util

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * 危险操作的倒计时确认框架（默认 5s）。
 *
 * 倒计时期间用户可随时取消，防止误触高风险操作（取消订单、结束跑步等）。
 * 初始有 1s 缓冲，给 TTS 播报提示语留出时间，再开始逐秒倒计时回调。
 *
 * 典型 ViewModel 用法：
 *   private val cancelAction = ConfirmableAction(
 *       scope = viewModelScope,
 *       onCountdown = { remaining -> _state.update { it.copy(cancelCountdown = remaining) } },
 *       onConfirm   = { cancelRunRequest() },
 *       onCancelled = { _state.update { it.copy(cancelCountdown = null) } },
 *   )
 *   fun onCancelPressed() = cancelAction.start()
 *   fun onCancelAborted() = cancelAction.cancel()
 */
class ConfirmableAction(
    private val scope: CoroutineScope,
    private val delaySeconds: Int = 5,
    private val onCountdown: (remaining: Int) -> Unit,
    private val onConfirm: suspend () -> Unit,
    private val onCancelled: () -> Unit = {},
) {
    private var job: Job? = null

    val isActive: Boolean get() = job?.isActive == true

    fun start() {
        job?.cancel()
        job = scope.launch {
            delay(1_000) // give TTS time to finish the initial prompt before counting down
            for (remaining in delaySeconds downTo 1) {
                onCountdown(remaining)
                delay(1_000)
            }
            job = null
            onConfirm()
        }
    }

    fun cancel() {
        if (job?.isActive == true) {
            job?.cancel()
            job = null
            onCancelled()
        }
    }
}
