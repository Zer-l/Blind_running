package com.guiderun.app.ui.blind.widget

import android.content.Context
import android.util.AttributeSet
import android.view.LayoutInflater
import android.view.View
import android.widget.LinearLayout
import androidx.annotation.StringRes
import com.google.android.material.button.MaterialButton
import com.guiderun.app.R

/**
 * 视障端页面底部操作区，统一结构：
 *
 * - **次按钮**：MaterialButton OutlinedButton，默认 GONE，位于 footer 上方，高度 64dp
 * - **主按钮**：LongPressGestureView（长按 2s + 5s 倒计时双段确认），位于 footer 最底部，高度 96dp
 *
 * 手势提示文字（"长按 2 秒..."）由页面级别的 `tv_action_hint` 独立 TextView 承担，
 * 放在 ScrollView 与 footer 之间，避免提示文本挤占热区底部按钮的视觉位置。
 * `primaryHint` 属性仍保留，用于设置主按钮的 contentDescription（TalkBack 朗读）。
 */
class BlindActionFooter @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : LinearLayout(context, attrs, defStyleAttr) {

    val primaryGesture: BlindLongPressGestureView
    val secondaryButton: MaterialButton

    init {
        orientation = VERTICAL
        LayoutInflater.from(context).inflate(R.layout.widget_blind_footer, this, true)
        primaryGesture = findViewById(R.id.btn_footer_primary)
        secondaryButton = findViewById(R.id.btn_footer_secondary)

        context.theme.obtainStyledAttributes(
            attrs,
            R.styleable.BlindActionFooter,
            0,
            0,
        ).apply {
            try {
                getString(R.styleable.BlindActionFooter_primaryLabel)?.let {
                    primaryGesture.text = it
                }
                getString(R.styleable.BlindActionFooter_primaryHint)?.let {
                    primaryGesture.contentDescription = it
                }
                getString(R.styleable.BlindActionFooter_secondaryLabel)?.let {
                    secondaryButton.text = it
                    secondaryButton.visibility = View.VISIBLE
                }
                if (getBoolean(R.styleable.BlindActionFooter_secondaryVisible, false)) {
                    secondaryButton.visibility = View.VISIBLE
                }
            } finally {
                recycle()
            }
        }
    }

    /** 一站式配置主操作与次操作。secondaryLabelRes 传 0 = 隐藏次按钮。 */
    fun configure(
        @StringRes primaryLabelRes: Int,
        @StringRes primaryHintRes: Int,
        @StringRes secondaryLabelRes: Int = 0,
    ) {
        primaryGesture.setText(primaryLabelRes)
        primaryGesture.contentDescription = context.getString(primaryHintRes)
        if (secondaryLabelRes != 0) {
            secondaryButton.setText(secondaryLabelRes)
            secondaryButton.visibility = View.VISIBLE
        } else {
            secondaryButton.visibility = View.GONE
        }
    }
}
