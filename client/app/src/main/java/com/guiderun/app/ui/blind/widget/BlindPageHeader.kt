package com.guiderun.app.ui.blind.widget

import android.content.Context
import android.util.AttributeSet
import android.view.LayoutInflater
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import com.guiderun.app.R
import com.guiderun.app.accessibility.TtsManager

/**
 * 视障端页面顶部统一结构：标题 + 状态行（默认 GONE，只在文本非空时显示）。
 *
 * - 标题：title 28sp，bold，居中，无 includeFontPadding（避免标题与下方分割线之间出现字体冗余间距）
 * - 状态：body 18sp，居中，accessibilityLiveRegion=polite（变化时自动朗读）；GONE 时不占空间
 *
 * widget 自身无 padding/margin，外间距完全由各页面 ConstraintLayout 控制。
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
                getString(R.styleable.BlindPageHeader_headerStatus)?.let {
                    tvStatus.text = it
                    if (it.isNotBlank()) tvStatus.visibility = View.VISIBLE
                }
            } finally {
                recycle()
            }
        }
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
            tvStatus.visibility = if (value.isBlank()) View.GONE else View.VISIBLE
        }

    /**
     * Fragment.onViewCreated/onResume 调用，让 TTS 播报完整页面摘要。
     * 默认 NORMAL：页面入场播报属于"被动状态描述"，可被用户操作（INTERACTION）抢占，
     * 避免页面进入瞬间用户已开始操作时听到无关的页面摘要。
     */
    fun announceOnEnter(
        tts: TtsManager,
        fullText: String,
        priority: TtsManager.Priority = TtsManager.Priority.NORMAL,
    ) {
        tts.speak(fullText, priority)
    }
}
