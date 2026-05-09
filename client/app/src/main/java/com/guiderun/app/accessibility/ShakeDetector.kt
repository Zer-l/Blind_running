package com.guiderun.app.accessibility

import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.sqrt

@Singleton
class ShakeDetector @Inject constructor() : SensorEventListener {

    /** 3 shakes within 1s → SOS */
    var onTripleShake: () -> Unit = {}

    /** 1 shake (no follow-up within 400ms) → announce status */
    var onSingleShake: (() -> Unit)? = null

    private var windowStartTime = 0L
    private var shakeCount = 0
    private val handler = Handler(Looper.getMainLooper())
    private var singleShakeRunnable: Runnable? = null

    override fun onSensorChanged(event: SensorEvent) {
        if (event.sensor.type != Sensor.TYPE_ACCELEROMETER) return
        val gx = event.values[0] / SensorManager.GRAVITY_EARTH
        val gy = event.values[1] / SensorManager.GRAVITY_EARTH
        val gz = event.values[2] / SensorManager.GRAVITY_EARTH
        val gForce = sqrt(gx * gx + gy * gy + gz * gz)
        if (gForce > SHAKE_THRESHOLD_G) {
            val now = SystemClock.elapsedRealtime()
            if (now - windowStartTime <= WINDOW_MS) {
                shakeCount++
            } else {
                windowStartTime = now
                shakeCount = 1
            }

            // Cancel pending single-shake callback (more shakes are coming)
            singleShakeRunnable?.let { handler.removeCallbacks(it) }
            singleShakeRunnable = null

            if (shakeCount >= REQUIRED_COUNT) {
                shakeCount = 0
                windowStartTime = 0L
                onTripleShake()
            } else if (shakeCount == 1 && onSingleShake != null) {
                // Wait 400ms to see if more shakes follow
                val callback = onSingleShake ?: return
                val r = Runnable { callback() }
                singleShakeRunnable = r
                handler.postDelayed(r, SINGLE_SHAKE_DELAY_MS)
            }
        }
    }

    override fun onAccuracyChanged(sensor: Sensor, accuracy: Int) {}

    fun register(sensorManager: SensorManager) {
        val accel = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER) ?: return
        sensorManager.registerListener(this, accel, SensorManager.SENSOR_DELAY_UI)
    }

    fun unregister(sensorManager: SensorManager) {
        sensorManager.unregisterListener(this)
        singleShakeRunnable?.let { handler.removeCallbacks(it) }
        singleShakeRunnable = null
    }

    companion object {
        private const val SHAKE_THRESHOLD_G = 2.7f
        private const val WINDOW_MS = 1_000L
        private const val REQUIRED_COUNT = 3
        private const val SINGLE_SHAKE_DELAY_MS = 400L
    }
}
