package com.guiderun.app.accessibility.asr

import android.content.Context
import com.guiderun.app.BuildConfig
import com.guiderun.app.R
import com.iflytek.cloud.ErrorCode
import com.iflytek.cloud.InitListener
import com.iflytek.cloud.RecognizerListener
import com.iflytek.cloud.RecognizerResult
import com.iflytek.cloud.SpeechConstant
import com.iflytek.cloud.SpeechError
import com.iflytek.cloud.SpeechRecognizer
import com.iflytek.cloud.SpeechUtility
import dagger.hilt.android.qualifiers.ApplicationContext
import org.json.JSONObject
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 讯飞 MSC SDK 实现的 ASR 引擎，用于：
 * - 视障端语音指令听写（结果交由 CommandParser 解析）
 * - 备注框自由文本听写
 *
 * 采用 IAT（语音听写）能力，云端识别，中文普通话，结果以多段 JSON 增量返回，最后一段 [isLast=true]。
 *
 * 注意：[SpeechUtility.createUtility] 应在 App 启动时调用一次；本类只负责按需创建 [SpeechRecognizer]。
 */
@Singleton
class IflytekAsrEngine @Inject constructor(
    @ApplicationContext private val context: Context,
) : AsrEngine {

    private var recognizer: SpeechRecognizer? = null
    private var pendingCallback: ((AsrResult) -> Unit)? = null
    private val resultBuffer = StringBuilder()

    /**
     * AppId 已配置且 SpeechUtility 已在 App.onCreate 初始化 → 视为可用。
     * 单独的 [SpeechRecognizer] 由首次 [start] 时懒创建，若失败再走 [AsrResult.Error] 反馈。
     */
    override val isAvailable: Boolean
        get() = BuildConfig.IFLYTEK_APPID.isNotBlank() && SpeechUtility.getUtility() != null

    init {
        if (BuildConfig.IFLYTEK_APPID.isBlank()) {
            Timber.w("IFLYTEK_APPID is empty; iflytek ASR will fall back to unavailable")
        }
    }

    override fun start(onResult: (AsrResult) -> Unit, eosMillis: Int) {
        if (BuildConfig.IFLYTEK_APPID.isBlank()) {
            onResult(AsrResult.Error(-1, context.getString(R.string.voice_input_init_failed)))
            onResult(AsrResult.Idle)
            return
        }
        pendingCallback = onResult
        resultBuffer.setLength(0)

        val r = recognizer ?: SpeechRecognizer.createRecognizer(context, initListener).also {
            recognizer = it
        }
        if (r == null) {
            onResult(AsrResult.Error(-1, context.getString(R.string.voice_input_init_failed)))
            onResult(AsrResult.Idle)
            return
        }
        applyDefaultParams(r, eosMillis)
        val ret = r.startListening(recognizerListener)
        if (ret != ErrorCode.SUCCESS) {
            Timber.w("iflytek startListening failed: $ret")
            onResult(AsrResult.Error(ret, humanize(ret)))
            onResult(AsrResult.Idle)
        }
    }

    override fun stop() {
        recognizer?.stopListening()
    }

    override fun cancel() {
        recognizer?.cancel()
        pendingCallback?.invoke(AsrResult.Idle)
        pendingCallback = null
    }

    override fun release() {
        recognizer?.cancel()
        recognizer?.destroy()
        recognizer = null
        pendingCallback = null
    }

    private fun applyDefaultParams(r: SpeechRecognizer, eosMillis: Int) {
        r.setParameter(SpeechConstant.PARAMS, null)
        r.setParameter(SpeechConstant.ENGINE_TYPE, SpeechConstant.TYPE_CLOUD)
        r.setParameter(SpeechConstant.RESULT_TYPE, "json")
        r.setParameter(SpeechConstant.LANGUAGE, "zh_cn")
        r.setParameter(SpeechConstant.ACCENT, "mandarin")
        // 静音超时：用户多久没说话视为超时
        r.setParameter(SpeechConstant.VAD_BOS, "4000")
        // 后端点静音检测：说完多久后停止录音并出结果。批量录入放宽以容忍分段停顿。
        r.setParameter(SpeechConstant.VAD_EOS, eosMillis.toString())
        // 1 表示带标点；指令场景标点会影响匹配，设为 0
        r.setParameter(SpeechConstant.ASR_PTT, "0")
    }

    private val initListener = InitListener { code ->
        if (code != ErrorCode.SUCCESS) {
            Timber.w("iflytek recognizer init failed: $code")
        }
    }

    private val recognizerListener = object : RecognizerListener {
        override fun onVolumeChanged(volume: Int, data: ByteArray?) = Unit
        override fun onEvent(eventType: Int, arg1: Int, arg2: Int, obj: android.os.Bundle?) = Unit

        override fun onBeginOfSpeech() {
            pendingCallback?.invoke(AsrResult.Ready)
        }

        override fun onEndOfSpeech() {
            pendingCallback?.invoke(AsrResult.EndOfSpeech)
        }

        override fun onResult(results: RecognizerResult?, isLast: Boolean) {
            val piece = parseIatJson(results?.resultString)
            resultBuffer.append(piece)
            val cb = pendingCallback
            if (isLast) {
                val finalText = resultBuffer.toString().trim()
                resultBuffer.setLength(0)
                cb?.invoke(AsrResult.Final(finalText))
                cb?.invoke(AsrResult.Idle)
                pendingCallback = null
            } else {
                cb?.invoke(AsrResult.Partial(resultBuffer.toString()))
            }
        }

        override fun onError(error: SpeechError?) {
            val code = error?.errorCode ?: -1
            val msg = humanize(code)
            Timber.w("iflytek error: code=$code, desc=${error?.getPlainDescription(true)}")
            val cb = pendingCallback
            cb?.invoke(AsrResult.Error(code, msg))
            cb?.invoke(AsrResult.Idle)
            pendingCallback = null
            resultBuffer.setLength(0)
        }
    }

    /** 讯飞 IAT 返回结构：{"ws":[{"cw":[{"w":"你好"}]}, ...]}，按 ws → cw → w 拼接 */
    private fun parseIatJson(json: String?): String {
        if (json.isNullOrBlank()) return ""
        return runCatching {
            val root = JSONObject(json)
            val ws = root.optJSONArray("ws") ?: return ""
            val sb = StringBuilder()
            for (i in 0 until ws.length()) {
                val cw = ws.getJSONObject(i).optJSONArray("cw") ?: continue
                if (cw.length() > 0) {
                    sb.append(cw.getJSONObject(0).optString("w"))
                }
            }
            sb.toString()
        }.getOrDefault("")
    }

    private fun humanize(errorCode: Int): String = when (errorCode) {
        ErrorCode.ERROR_NO_NETWORK,
        ErrorCode.ERROR_NETWORK_TIMEOUT,
        ErrorCode.ERROR_NET_EXCEPTION -> context.getString(R.string.voice_input_error_network)

        ErrorCode.ERROR_NO_MATCH,
        ErrorCode.ERROR_NO_SPEECH,
        ErrorCode.ERROR_SPEECH_TIMEOUT,
        ErrorCode.ERROR_EMPTY_UTTERANCE -> context.getString(R.string.voice_input_error_no_match)

        ErrorCode.ERROR_AUDIO_RECORD,
        ErrorCode.ERROR_PERMISSION_DENIED -> context.getString(R.string.voice_input_permission_denied)

        ErrorCode.ERROR_INVALID_RESULT,
        ErrorCode.ERROR_INTERRUPT,
        ErrorCode.ERROR_ENGINE_BUSY -> context.getString(R.string.voice_input_error_busy)

        else -> context.getString(R.string.voice_input_error_generic)
    }
}
