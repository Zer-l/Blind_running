package com.guiderun.app.ui.blind.widget

import android.content.Context
import android.util.AttributeSet
import android.view.LayoutInflater
import android.widget.LinearLayout
import android.widget.TextView
import com.guiderun.app.R
import com.guiderun.app.accessibility.TtsManager

/**
 * 视障端页面顶部统一结构：标题 + 状态行。
 *
 * - 标题：headline 22sp，bold，居中
 * - 状态：body 18sp，居中，accessibilityLiveRegion=polite（变化时自动朗读）
 *
 * Fragment.onViewCreated 调用 [announceOnEnter] 让 TtsManager 自动播报页面摘要。
 */
class BlindPageHeader @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : LinearLayout(context, attrs, defStyleAttr) {

    private val tvTitle: TextView
    private val tvStatus: TextView

    init {
        orientation = VERTICAL
        LayoutInflater.from(context).inflate(R.layout.widget_blind_header, this, true)
        tvTitle = findViewById(R.id.tv_header_title)
        tvStatus = findViewById(R.id.tv_header_status)

        context.theme.obtainStyledAttributes(
            attrs,
            R.styleable.BlindPageHeader,
            0,
            0,
        ).apply {
            try {
                getString(R.styleable.BlindPageHeader_headerTitle)?.let { tvTitle.text = it }
                getString(R.styleable.BlindPageHeader_headerStatus)?.let { tvStatus.text = it }
            } finally {
                recycle()
            }
        }

        val pad = resources.getDimensionPixelSize(R.dimen.blind_space_md)
        setPadding(pad, pad, pad, pad)
    }

    var title: CharSequence
        get() = tvTitle.text
        set(value) {
            tvTitle.text = value
            tvTitle.contentDescription = value
        }

    var status: CharSequence
        get() = tvStatus.text
        set(value) {
            tvStatus.text = value
            tvStatus.contentDescription = value
        }

    /** Fragment.onViewCreated/onResume 调用，让 TTS 播报完整页面摘要。 */
    fun announceOnEnter(
        tts: TtsManager,
        fullText: String,
        priority: TtsManager.Priority = TtsManager.Priority.HIGH,
    ) {
        tts.speak(fullText, priority)
    }
}
