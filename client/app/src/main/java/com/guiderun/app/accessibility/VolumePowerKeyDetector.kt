package com.guiderun.app.accessibility

import android.view.KeyEvent
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.abs

@Singleton
class VolumePowerKeyDetector @Inject constructor() {

    var onTrigger: (() -> Unit)? = null

    private var volumeUpPressedAt: Long = 0
    private var powerPressedAt: Long = 0

    fun onKeyDown(keyCode: Int): Boolean {
        val now = System.currentTimeMillis()
        when (keyCode) {
            KeyEvent.KEYCODE_VOLUME_UP -> {
                volumeUpPressedAt = now
                checkCombo(now)
            }
            KeyEvent.KEYCODE_POWER -> {
                powerPressedAt = now
                checkCombo(now)
            }
        }
        return false
    }

    private fun checkCombo(now: Long) {
        if (volumeUpPressedAt > 0 && powerPressedAt > 0 &&
            abs(volumeUpPressedAt - powerPressedAt) < COMBO_WINDOW_MS
        ) {
            onTrigger?.invoke()
            volumeUpPressedAt = 0
            powerPressedAt = 0
        }
    }

    companion object {
        private const val COMBO_WINDOW_MS = 500L
    }
}
