package com.guiderun.app.ui.blind.widget

import android.content.Context
import android.util.AttributeSet
import android.view.LayoutInflater
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.content.res.AppCompatResources
import com.guiderun.app.R

/**
 * 视障端通用数据展示卡片：大数字 + 单位 + 标签。
 *
 * - 标签：label 16sp，标识 "距离"/"时长"/"配速"
 * - 数值：display 48sp，bold，主调色，关键信息
 * - 单位：label 16sp，"公里"/"分钟"
 *
 * TalkBack：整张卡片作为单个可聚焦元素，contentDescription = "$label $ttsValue $unit"。
 * 通过 setValue(value, ttsRead) 可分别指定视觉值与朗读值（如视觉 "3.25" / 朗读 "三点二五"）。
 */
class BlindMetricCard @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : LinearLayout(context, attrs, defStyleAttr) {

    private val tvLabel: TextView
    private val tvValue: TextView
    private val tvUnit: TextView

    init {
        orientation = VERTICAL
        LayoutInflater.from(context).inflate(R.layout.widget_blind_metric_card, this, true)
        tvLabel = findViewById(R.id.tv_metric_label)
        tvValue = findViewById(R.id.tv_metric_value)
        tvUnit = findViewById(R.id.tv_metric_unit)

        context.theme.obtainStyledAttributes(
            attrs,
            R.styleable.BlindMetricCard,
            0,
            0,
        ).apply {
            try {
                getString(R.styleable.BlindMetricCard_metricLabel)?.let { tvLabel.text = it }
                getString(R.styleable.BlindMetricCard_metricUnit)?.let { tvUnit.text = it }
                getString(R.styleable.BlindMetricCard_metricValue)?.let { tvValue.text = it }
            } finally {
                recycle()
            }
        }

        background = AppCompatResources.getDrawable(context, R.drawable.bg_blind_card)
        val pad = resources.getDimensionPixelSize(R.dimen.blind_space_md)
        setPadding(pad, pad, pad, pad)
        minimumHeight = resources.getDimensionPixelSize(R.dimen.blind_touch_min)

        isFocusable = true
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_YES
        refreshContentDescription()
    }

    var label: CharSequence
        get() = tvLabel.text
        set(value) {
            tvLabel.text = value
            refreshContentDescription()
        }

    var unit: CharSequence
        get() = tvUnit.text
        set(value) {
            tvUnit.text = value
            refreshContentDescription()
        }

    val value: CharSequence get() = tvValue.text

    /** 设置主数值。ttsRead 为 TalkBack 朗读值，不传则用 value 自身。 */
    fun setValue(value: String, ttsRead: String? = null) {
        tvValue.text = value
        refreshContentDescription(ttsRead ?: value)
    }

    private fun refreshContentDescription(ttsValue: String = tvValue.text.toString()) {
        val label = tvLabel.text.toString()
        val unit = tvUnit.text.toString()
        contentDescription = listOf(label, ttsValue, unit)
            .filter { it.isNotBlank() }
            .joinToString(" ")
    }
}
