package com.guiderun.app.accessibility.asr

/**
 * 语音识别引擎抽象。
 *
 * 实现要求：
 * - 主线程调用 [start]/[stop]/[cancel]/[release]（绝大多数 ASR SDK 的硬性约束）。
 * - 回调 [onResult] 也保证在主线程触发。
 * - 同一时刻仅有一次识别活动；调用方在 [AsrResult.Final] / [AsrResult.Error] / [AsrResult.Idle] 之后才能再次 [start]。
 *
 * 实现切换：通过 Hilt 注入，目前为讯飞 MSC（[IflytekAsrEngine]）。
 */
interface AsrEngine {
    /** 引擎是否可用（初始化完成、SDK 已加载等） */
    val isAvailable: Boolean

    /**
     * 开始一次识别。
     * @param onResult 状态回调（主线程）。整条生命周期内会被多次回调，直到 [AsrResult.Idle] 表示结束。
     * @param eosMillis 后端点静音检测（VAD_EOS）：说完多久静音视为结束。
     *        指令听写用默认短值（说完即出），批量录入需放宽以容忍分段停顿。
     */
    fun start(onResult: (AsrResult) -> Unit, eosMillis: Int = DEFAULT_EOS_MILLIS)

    companion object {
        /** 指令听写默认后端点静音时长（ms）：短句说完即出。 */
        const val DEFAULT_EOS_MILLIS = 1200

        /** 批量录入后端点静音时长（ms）：容忍"地点…时长…备注…"分段口述间的停顿。 */
        const val BATCH_EOS_MILLIS = 3000
    }

    /** 主动结束录音并进入识别阶段（用于"说完了"按钮） */
    fun stop()

    /** 取消本次识别，不返回结果 */
    fun cancel()

    /** 释放资源（一般跟随 Application 生命周期，由 Hilt Singleton 管理） */
    fun release()
}
