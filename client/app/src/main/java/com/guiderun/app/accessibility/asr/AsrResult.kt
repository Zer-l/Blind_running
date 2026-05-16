package com.guiderun.app.accessibility.asr

/**
 * ASR 引擎统一回调结果。
 *
 * 状态流：[Ready] → [Partial]* → [Final] → [Idle]
 * 出错则：→ [Error] → [Idle]
 */
sealed interface AsrResult {
    /** SDK 内部录音机已就绪，可以开始说话 */
    data object Ready : AsrResult

    /** VAD 检测到语音结束，进入识别处理阶段（不再录音） */
    data object EndOfSpeech : AsrResult

    /** 流式中间结果，可用于实时显示但通常不参与指令解析 */
    data class Partial(val text: String) : AsrResult

    /** 最终识别结果。一次识别仅触发一次 */
    data class Final(val text: String) : AsrResult

    /** 识别错误。code 取自具体引擎，message 已是用户可读文案 */
    data class Error(val code: Int, val message: String) : AsrResult

    /** 已停止 / 取消 / 释放 */
    data object Idle : AsrResult
}
