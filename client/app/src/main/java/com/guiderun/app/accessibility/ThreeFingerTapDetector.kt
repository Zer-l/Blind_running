package com.guiderun.app.accessibility

import android.view.MotionEvent

/**
 * Detects a simultaneous 3-finger tap.
 * Attach to a root view's dispatchTouchEvent or onTouchEvent.
 *
 * Usage:
 *   private val threeFingerDetector = ThreeFingerTapDetector { onSpeechActivated() }
 *   override fun dispatchTouchEvent(ev: MotionEvent): Boolean {
 *       threeFingerDetector.onTouchEvent(ev)
 *       return super.dispatchTouchEvent(ev)
 *   }
 */
class ThreeFingerTapDetector(private val onThreeFingerTap: () -> Unit) {

    private val pointerDownTimes = mutableMapOf<Int, Long>()
    private var maxPointerCount = 0

    fun onTouchEvent(event: MotionEvent) {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN, MotionEvent.ACTION_POINTER_DOWN -> {
                val index = event.actionIndex
                val id = event.getPointerId(index)
                pointerDownTimes[id] = event.eventTime
                maxPointerCount = maxOf(maxPointerCount, event.pointerCount)
            }

            MotionEvent.ACTION_UP -> {
                // Final finger lifted; check if this was a clean 3-finger tap
                if (maxPointerCount == 3) {
                    val allFast = pointerDownTimes.values.all { downTime ->
                        event.eventTime - downTime <= TAP_TIMEOUT_MS
                    }
                    if (allFast) onThreeFingerTap()
                }
                pointerDownTimes.clear()
                maxPointerCount = 0
            }

            MotionEvent.ACTION_CANCEL -> {
                pointerDownTimes.clear()
                maxPointerCount = 0
            }
        }
    }

    companion object {
        private const val TAP_TIMEOUT_MS = 300L
    }
}
