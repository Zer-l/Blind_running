package com.guiderun.app.ui.blind.widget

import android.content.Context
import android.util.AttributeSet
import android.view.LayoutInflater
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import androidx.annotation.StringRes
import com.google.android.material.button.MaterialButton
import com.guiderun.app.R

/**
 * 视障端页面底部操作区，统一结构：
 *
 * - **主按钮**：LongPressGestureView（长按 2s + 5s 倒计时双段确认），高度 96dp
 * - **次按钮**：MaterialButton OutlinedButton，默认 GONE，高度 64dp
 * - **提示文字**：hint 14sp，告诉用户"长按 2 秒 X，5 秒后 X"
 *
 * Fragment 通过 [primaryGesture] 调用 bind() 注册回调；通过 [secondaryButton] 设置次操作。
 */
class BlindActionFooter @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : LinearLayout(context, attrs, defStyleAttr) {

    val primaryGesture: LongPressGestureView
    val secondaryButton: MaterialButton
    val hintText: TextView

    init {
        orientation = VERTICAL
        LayoutInflater.from(context).inflate(R.layout.widget_blind_footer, this, true)
        primaryGesture = findViewById(R.id.btn_footer_primary)
        secondaryButton = findViewById(R.id.btn_footer_secondary)
        hintText = findViewById(R.id.tv_footer_hint)

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
                    hintText.text = it
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
        hintText.setText(primaryHintRes)
        primaryGesture.contentDescription = context.getString(primaryHintRes)
        if (secondaryLabelRes != 0) {
            secondaryButton.setText(secondaryLabelRes)
            secondaryButton.visibility = View.VISIBLE
        } else {
            secondaryButton.visibility = View.GONE
        }
    }
}
