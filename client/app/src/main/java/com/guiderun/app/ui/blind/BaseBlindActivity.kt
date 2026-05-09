package com.guiderun.app.ui.blind

import android.hardware.SensorManager
import android.os.Bundle
import android.view.KeyEvent
import android.view.MotionEvent
import androidx.appcompat.app.AppCompatActivity
import com.guiderun.app.accessibility.ShakeDetector
import com.guiderun.app.accessibility.SosCoordinator
import com.guiderun.app.accessibility.VolumePowerKeyDetector
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
abstract class BaseBlindActivity : AppCompatActivity() {

    @Inject lateinit var shakeDetector: ShakeDetector
    @Inject lateinit var sosCoordinator: SosCoordinator
    @Inject lateinit var volumePowerKeyDetector: VolumePowerKeyDetector

    /** Active fragment registers for single-shake status announcement. */
    var onSingleShakeCallback: (() -> Unit)? = null

    /** Active fragment sets this to the current requestId so SOS can call the API. */
    var activeRequestId: String? = null

    /** Active fragment registers a touch forwarder for ThreeFingerTap or long-press detection. */
    var touchEventForwarder: ((MotionEvent) -> Unit)? = null

    private lateinit var sensorManager: SensorManager

    private var volumeDownSince: Long = 0L
    private var volumeDownLongFired: Boolean = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        sensorManager = getSystemService(SENSOR_SERVICE) as SensorManager
        shakeDetector.onSingleShake = ::handleSingleShake
        volumePowerKeyDetector.onTrigger = { sosCoordinator.trigger(activeRequestId) }
    }

    override fun onResume() {
        super.onResume()
        shakeDetector.register(sensorManager)
    }

    override fun onPause() {
        super.onPause()
        shakeDetector.unregister(sensorManager)
    }

    override fun dispatchTouchEvent(event: MotionEvent): Boolean {
        touchEventForwarder?.invoke(event)
        return super.dispatchTouchEvent(event)
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        // Volume UP + Power combo detection
        if (event.keyCode == KeyEvent.KEYCODE_VOLUME_UP || event.keyCode == KeyEvent.KEYCODE_POWER) {
            if (event.action == KeyEvent.ACTION_DOWN) {
                volumePowerKeyDetector.onKeyDown(event.keyCode)
            }
        }

        // Single volume UP for status announcement
        if (event.keyCode == KeyEvent.KEYCODE_VOLUME_UP) {
            if (event.action == KeyEvent.ACTION_DOWN && event.repeatCount == 0) {
                onSingleShakeCallback?.invoke()
            }
            return true
        }
        if (event.keyCode == KeyEvent.KEYCODE_VOLUME_DOWN) {
            when (event.action) {
                KeyEvent.ACTION_DOWN -> {
                    if (event.repeatCount == 0) {
                        volumeDownSince = System.currentTimeMillis()
                    } else if (event.isLongPress || System.currentTimeMillis() - volumeDownSince >= 2_000L) {
                        if (!volumeDownLongFired) {
                            volumeDownLongFired = true
                            sosCoordinator.trigger(activeRequestId)
                        }
                    }
                }
                KeyEvent.ACTION_UP -> {
                    volumeDownSince = 0L
                    volumeDownLongFired = false
                }
            }
            return true
        }
        return super.dispatchKeyEvent(event)
    }

    private fun handleSingleShake() {
        onSingleShakeCallback?.invoke()
    }
}
