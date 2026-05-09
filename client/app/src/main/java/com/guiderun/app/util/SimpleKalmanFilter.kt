package com.guiderun.app.util

/**
 * 简化卡尔曼滤波器，用于GPS轨迹平滑。
 *
 * @param processNoise 过程噪声，越小越信任预测（轨迹越平滑），默认 0.001
 * @param measurementNoise 测量噪声基准（米），越小越信任测量，默认 5.0
 */
class SimpleKalmanFilter(
    private val processNoise: Double = 0.001,
    private val measurementNoise: Double = 5.0,
) {
    private var estimatedLat = 0.0
    private var estimatedLng = 0.0
    private var errorCovariance = 1.0
    private var initialized = false

    /**
     * 输入新的测量值，返回滤波后的坐标。
     *
     * @param measuredLat 测量纬度
     * @param measuredLng 测量经度
     * @param accuracy GPS精度（米），用于动态调整测量噪声
     * @return 滤波后的 (lat, lng)
     */
    fun update(measuredLat: Double, measuredLng: Double, accuracy: Float = 10f): Pair<Double, Double> {
        if (!initialized) {
            estimatedLat = measuredLat
            estimatedLng = measuredLng
            initialized = true
            return Pair(estimatedLat, estimatedLng)
        }

        // 预测步骤：误差协方累加过程噪声
        val predictedError = errorCovariance + processNoise

        // 根据GPS精度动态调整测量噪声
        // 精度差（accuracy大）→ 测量噪声大 → 更信任预测（平滑）
        // 精度好（accuracy小）→ 测量噪声小 → 更信任测量
        val noiseScale = (accuracy / 10.0).coerceIn(0.5, 10.0)
        val adjustedNoise = measurementNoise * noiseScale

        // 卡尔曼增益
        val kalmanGain = predictedError / (predictedError + adjustedNoise)

        // 更新估计
        estimatedLat += kalmanGain * (measuredLat - estimatedLat)
        estimatedLng += kalmanGain * (measuredLng - estimatedLng)

        // 更新误差协方差
        errorCovariance = (1 - kalmanGain) * predictedError

        return Pair(estimatedLat, estimatedLng)
    }

    /**
     * 重置滤波器状态。
     */
    fun reset() {
        initialized = false
        errorCovariance = 1.0
    }
}
