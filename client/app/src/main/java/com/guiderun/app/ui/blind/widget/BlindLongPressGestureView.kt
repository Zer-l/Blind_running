package com.guiderun.app.ui.blind.widget

import android.content.Context
import android.content.res.ColorStateList
import android.util.AttributeSet
import android.util.TypedValue
import android.view.MotionEvent
import android.view.accessibility.AccessibilityManager
import androidx.annotation.StringRes
import androidx.lifecycle.LifecycleCoroutineScope
import com.google.android.material.button.MaterialButton
import com.guiderun.app.R
import com.guiderun.app.accessibility.HapticFeedback
import com.guiderun.app.accessibility.TtsManager
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import timber.log.Timber

/**
 * 视障端通用长按手势组件，统一"长按阈值 + 倒计时撤销"双段确认模型。
 *
 * 状态机：
 *   IDLE ─DOWN→ PRESSING ─thresholdMs→ COUNTDOWN ─countdownMs→ (committed) ─→ IDLE
 *                  │UP                      │UP / CANCEL
 *                  ▼ 静默重置                ▼ 撤销（warning + TTS）
 *                IDLE                      IDLE
 *
 * TalkBack 兼容：
 * - 普通模式：onTouchListener 接管 ACTION_DOWN/UP 实现渐进式手势
 * - TalkBack 模式（touchExploration=ON）：触摸事件由系统接管，双击会触发 performClick，
 *   此时直接走 commit 路径（跳过 2s+5s 渐进，因为 TalkBack 用户已明确表达意图）
 *
 * XML 用法：
 *   <BlindLongPressGestureView
 *       app:thresholdMs="2000"
 *       app:countdownMs="5000"
 *       app:thresholdLabel="@string/blind_hint_long_press_threshold"
 *       app:countdownLabel="@string/blind_tts_cancelled" />
 *
 * Kotlin 用法：onViewCreated 调用 bind(scope, tts, haptic, ..., onCountdownCommitted = { ... })
 */
class BlindLongPressGestureView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = com.google.android.material.R.attr.materialButtonStyle,
) : MaterialButton(context, attrs, defStyleAttr) {

    private var thresholdMs: Long = DEFAULT_THRESHOLD_MS
    private var countdownMs: Long = DEFAULT_COUNTDOWN_MS
    private var tickHapticEnabled: Boolean = true
    private var announceEverySecond: Boolean = true
    private var thresholdLabelRes: Int = 0
    private var countdownLabelRes: Int = 0

    private var scope: LifecycleCoroutineScope? = null
    private var tts: TtsManager? = null
    private var haptic: HapticFeedback? = null
    private var onThresholdReached: (suspend () -> Unit)? = null
    private var onCountdownCommitted: (() -> Unit)? = null
    private var onCancel: (() -> Unit)? = null

    /**
     * 手势按下（ACTION_DOWN，进入 PRESSING）即触发。
     * 用于让页面抑制定时/轮询自动播报，避免在长按 0~2s 静默窗口内抢播打断阈值/倒计时提示。
     */
    private var onGestureStart: (() -> Unit)? = null

    /**
     * 手势结束（手指抬起 / 取消，或 [reset] 主动复位）即触发，与 [onGestureStart] 配对。
     * 用于让页面精确感知"是否正在长按"，例如跑步页据此屏蔽自动暂停的"已暂停"播报。
     */
    private var onGestureEnd: (() -> Unit)? = null

    /**
     * 是否允许启动长按手势。返回 false 时，按下只读 contentDescription 状态提示、
     * 不进入 2s 阈值 / 5s 倒计时，避免在动作尚不可执行时播报误导性的阈值文案
     * （如志愿者未到达时按"确认汇合"却播"5秒后开始跑步"）。
     */
    private var canStartGesture: () -> Boolean = { true }

    private var job: Job? = null

    // state 仅在主线程访问（onTouch 回调 + lifecycleScope 默认 Main.immediate 协程），无需 @Volatile
    private var state: State = State.IDLE

    private val accessibilityManager: AccessibilityManager =
        context.getSystemService(Context.ACCESSIBILITY_SERVICE) as AccessibilityManager

    init {
        context.theme.obtainStyledAttributes(
            attrs,
            R.styleable.BlindLongPressGestureView,
            0,
            0,
        ).apply {
            try {
                thresholdMs = getInt(
                    R.styleable.BlindLongPressGestureView_thresholdMs,
                    DEFAULT_THRESHOLD_MS.toInt(),
                ).toLong()
                countdownMs = getInt(
                    R.styleable.BlindLongPressGestureView_countdownMs,
                    DEFAULT_COUNTDOWN_MS.toInt(),
                ).toLong()
                tickHapticEnabled = getBoolean(
                    R.styleable.BlindLongPressGestureView_tickHapticEnabled,
                    true,
                )
                announceEverySecond = getBoolean(
                    R.styleable.BlindLongPressGestureView_announceEverySecond,
                    true,
                )
                thresholdLabelRes = getResourceId(
                    R.styleable.BlindLongPressGestureView_thresholdLabel,
                    0,
                )
                countdownLabelRes = getResourceId(
                    R.styleable.BlindLongPressGestureView_countdownLabel,
                    0,
                )
            } finally {
                recycle()
            }
        }
        isFocusable = true
        isClickable = true

        // 焦点可视化：低视力用户依赖 D-pad / 键盘导航时需要看见焦点
        val focusRingPx = resources.getDimensionPixelSize(R.dimen.blind_stroke_focus_ring)
        val focusRingColor = resolveAttrColor(R.attr.blindFocusRing)
        setOnFocusChangeListener { _, hasFocus ->
            if (hasFocus) {
                strokeWidth = focusRingPx
                strokeColor = ColorStateList.valueOf(focusRingColor)
            } else {
                strokeWidth = 0
            }
        }
    }

    private fun resolveAttrColor(attrRes: Int): Int {
        val tv = TypedValue()
        context.theme.resolveAttribute(attrRes, tv, true)
        return tv.data
    }

    /**
     * 绑定生命周期、依赖与回调。Fragment.onViewCreated 调用一次。
     */
    fun bind(
        scope: LifecycleCoroutineScope,
        ttsManager: TtsManager,
        hapticFeedback: HapticFeedback,
        @StringRes thresholdLabelRes: Int = this.thresholdLabelRes,
        @StringRes countdownLabelRes: Int = this.countdownLabelRes,
        onThresholdReached: suspend () -> Unit = {},
        onCountdownCommitted: () -> Unit,
        onCancel: () -> Unit = {},
        canStartGesture: () -> Boolean = { true },
        onGestureStart: () -> Unit = {},
        onGestureEnd: () -> Unit = {},
    ) {
        this.scope = scope
        this.tts = ttsManager
        this.haptic = hapticFeedback
        this.thresholdLabelRes = thresholdLabelRes
        this.countdownLabelRes = countdownLabelRes
        this.onThresholdReached = onThresholdReached
        this.onCountdownCommitted = onCountdownCommitted
        this.onCancel = onCancel
        this.canStartGesture = canStartGesture
        this.onGestureStart = onGestureStart
        this.onGestureEnd = onGestureEnd
        setupListeners()
    }

    /** 按下时不可启动手势：仅读状态提示，不进倒计时。 */
    private fun speakHintOnly() {
        haptic?.tick()
        val hint = contentDescription ?: text
        if (!hint.isNullOrBlank()) {
            tts?.speak(hint.toString(), TtsManager.Priority.INTERACTION)
        }
    }

    private fun setupListeners() {
        setOnTouchListener { _, event ->
            // TalkBack 启用时，触摸事件由系统接管，这里不会收到原始 DOWN/UP，
            // performClick 由系统派发后由 setOnClickListener 处理。
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    // 手势不可启动（如志愿者未到达）：只读状态提示，不进 2s/5s 倒计时
                    if (!canStartGesture()) speakHintOnly() else startGesture()
                    true
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    handleRelease()
                    true
                }
                else -> false
            }
        }
        setOnClickListener {
            // 仅 TalkBack 模式会到这里（普通模式 onTouchListener 消费了 ACTION_UP，
            // performClick 不会被触发）。TalkBack 用户双击 = 明确确认，跳过渐进确认。
            if (accessibilityManager.isTouchExplorationEnabled) {
                // 不可启动时只读提示，不执行确认
                if (!canStartGesture()) {
                    speakHintOnly()
                } else {
                    haptic?.confirm()
                    onCountdownCommitted?.invoke()
                }
            }
        }
    }

    private fun startGesture() {
        if (state != State.IDLE) return
        job?.cancel()
        state = State.PRESSING
        // 按下即通知页面抑制定时/轮询播报，防止 0~2s 静默窗口被抢播
        onGestureStart?.invoke()
        if (tickHapticEnabled) haptic?.tick()
        val activeScope = scope ?: return
        job = activeScope.launch {
            delay(thresholdMs)
            if (state != State.PRESSING) return@launch

            state = State.COUNTDOWN
            haptic?.confirm()
            // 先执行外部回调（可 suspend，用于播报动态内容），再播报静态文本
            onThresholdReached?.invoke()
            if (thresholdLabelRes != 0) {
                // 阈值播报：INTERACTION FLUSH，打断之前任何播报，启动 INTERACTION 锁
                tts?.speakAndWait(
                    context.getString(thresholdLabelRes),
                    TtsManager.Priority.INTERACTION,
                )
            }

            val totalSecs = (countdownMs / 1000L).toInt()
            for (i in totalSecs downTo 1) {
                if (state != State.COUNTDOWN) return@launch
                if (announceEverySecond) {
                    // 倒计时数字：INTERACTION + flush=false 接力排队，不打断阈值/上一秒数字，
                    // 但仍持有 INTERACTION 锁让外部 HIGH/NORMAL 等待
                    tts?.speak(i.toString(), TtsManager.Priority.INTERACTION, flush = false)
                }
                if (tickHapticEnabled) haptic?.tick()
                delay(1000L)
            }

            if (state == State.COUNTDOWN) {
                state = State.IDLE
                haptic?.confirm()
                onCountdownCommitted?.invoke()
            }
        }
    }

    private fun handleRelease() {
        val prev = state
        val activeJob = job
        state = State.IDLE
        job = null
        activeJob?.cancel()
        when (prev) {
            State.PRESSING -> {
                // 阈值前松开 = 视障用户误以为是普通点击按钮，播报长按提示纠正交互模型。
                // INTERACTION 优先级：保证立即播且不被其他低优消息打断
                if (tickHapticEnabled) haptic?.tick()
                val hint = contentDescription ?: text
                if (!hint.isNullOrBlank()) {
                    tts?.speak(hint.toString(), TtsManager.Priority.INTERACTION)
                }
            }
            State.COUNTDOWN -> {
                haptic?.warning()
                if (countdownLabelRes != 0) {
                    // 撤销反馈：INTERACTION FLUSH 打断正在播的倒计时数字
                    tts?.speak(
                        context.getString(countdownLabelRes),
                        TtsManager.Priority.INTERACTION,
                    )
                }
                onCancel?.invoke()
            }
            State.IDLE -> Unit
        }
        // 手指抬起/取消 = 长按结束（注意 commit 在倒计时走完时触发，此时手指可能仍按着，
        // 故 end 只跟随物理抬手，确保"结束跑步播报"期间不被自动暂停播报打断）
        onGestureEnd?.invoke()
    }

    /** 动态切换阈值播报文案（如按钮在"取消订单/确认汇合"间随状态切换）。 */
    fun setThresholdLabel(@StringRes resId: Int) {
        thresholdLabelRes = resId
    }

    /** 主动重置到 IDLE，取消正在进行的手势。Fragment.onPause 可调用。 */
    fun reset() {
        job?.cancel()
        job = null
        state = State.IDLE
        onGestureEnd?.invoke()
    }

    override fun onDetachedFromWindow() {
        reset()
        super.onDetachedFromWindow()
    }

    private enum class State { IDLE, PRESSING, COUNTDOWN }

    companion object {
        private const val DEFAULT_THRESHOLD_MS: Long = 2_000L
        private const val DEFAULT_COUNTDOWN_MS: Long = 5_000L
    }
}
