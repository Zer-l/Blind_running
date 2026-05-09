package com.guiderun.app.ui.blind

import android.media.MediaRecorder
import android.os.Bundle
import android.os.SystemClock
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import androidx.core.os.bundleOf
import androidx.fragment.app.Fragment
import androidx.fragment.app.setFragmentResult
import androidx.lifecycle.lifecycleScope
import com.guiderun.app.R
import com.guiderun.app.accessibility.HapticFeedback
import com.guiderun.app.accessibility.TtsManager
import com.guiderun.app.data.remote.api.UploadApi
import com.guiderun.app.databinding.FragmentVoiceRecordBinding
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.asRequestBody
import java.io.File
import javax.inject.Inject

@AndroidEntryPoint
class VoiceRecordFragment : Fragment() {

    @Inject lateinit var ttsManager: TtsManager
    @Inject lateinit var hapticFeedback: HapticFeedback
    @Inject lateinit var uploadApi: UploadApi

    private var _binding: FragmentVoiceRecordBinding? = null
    private val binding get() = _binding!!

    private var recorder: MediaRecorder? = null
    private var outputFile: File? = null
    private var recordingStartTimeMs: Long = 0L
    private var maxDurationJob: kotlinx.coroutines.Job? = null
    private var vibrationJob: kotlinx.coroutines.Job? = null
    private var lastTapTimeMs: Long = 0L

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        _binding = FragmentVoiceRecordBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        ttsManager.speak("请在听到提示音后说出反馈，最长30秒，说完双击屏幕结束", TtsManager.Priority.HIGH)
    }

    override fun onResume() {
        super.onResume()
        ttsManager.acquire()
        startRecording()
        (activity as? BaseBlindActivity)?.apply {
            touchEventForwarder = ::onTouchEvent
        }
    }

    override fun onPause() {
        super.onPause()
        stopRecordingAndVibration()
        ttsManager.release()
        (activity as? BaseBlindActivity)?.apply {
            touchEventForwarder = null
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    private fun startRecording() {
        try {
            val file = File.createTempFile("voice_", ".m4a", requireContext().cacheDir)
            outputFile = file
            recorder = MediaRecorder().apply {
                setAudioSource(MediaRecorder.AudioSource.MIC)
                setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                setAudioEncodingBitRate(64_000)
                setAudioSamplingRate(44_100)
                setMaxDuration(30_000)
                setOutputFile(file.absolutePath)
                setOnInfoListener { _, what, _ ->
                    if (what == MediaRecorder.MEDIA_RECORDER_INFO_MAX_DURATION_REACHED) {
                        viewLifecycleOwner.lifecycleScope.launch {
                            ttsManager.speak("已达到最大时长", TtsManager.Priority.HIGH)
                            stopAndUpload()
                        }
                    }
                }
                prepare()
                start()
            }
            recordingStartTimeMs = SystemClock.elapsedRealtime()
            hapticFeedback.confirm()

            startVibrationLoop()
            startMaxDurationTimer()
            startTimerDisplay()
        } catch (e: Exception) {
            ttsManager.speak("录音启动失败", TtsManager.Priority.HIGH)
            hapticFeedback.error()
        }
    }

    private fun startVibrationLoop() {
        vibrationJob = viewLifecycleOwner.lifecycleScope.launch {
            while (true) {
                hapticFeedback.tick()
                delay(2_000)
            }
        }
    }

    private fun startMaxDurationTimer() {
        maxDurationJob = viewLifecycleOwner.lifecycleScope.launch {
            delay(30_000)
            stopAndUpload()
        }
    }

    private fun startTimerDisplay() {
        viewLifecycleOwner.lifecycleScope.launch {
            while (true) {
                delay(1_000)
                val elapsed = ((SystemClock.elapsedRealtime() - recordingStartTimeMs) / 1000).toInt()
                val remaining = (30 - elapsed).coerceAtLeast(0)
                binding.tvTimer.text = "%02d:%02d".format(remaining / 60, remaining % 60)
            }
        }
    }

    private fun onTouchEvent(event: MotionEvent) {
        if (event.actionMasked == MotionEvent.ACTION_DOWN) {
            val now = SystemClock.elapsedRealtime()
            if (now - lastTapTimeMs < 400) {
                // Double tap detected
                stopAndUpload()
            }
            lastTapTimeMs = now
        }
    }

    private fun stopAndUpload() {
        stopRecordingAndVibration()
        binding.tvStatus.text = getString(R.string.voice_record_uploading)
        ttsManager.speak("正在上传录音", TtsManager.Priority.NORMAL)

        val file = outputFile ?: run {
            returnResult(null)
            return
        }

        viewLifecycleOwner.lifecycleScope.launch {
            try {
                val requestFile = file.asRequestBody("audio/m4a".toMediaType())
                val part = MultipartBody.Part.createFormData("file", file.name, requestFile)
                val response = withContext(Dispatchers.IO) { uploadApi.uploadVoice(part) }
                val voiceUrl = response.data?.voiceUrl
                if (voiceUrl != null) {
                    ttsManager.speak("录音上传成功", TtsManager.Priority.HIGH)
                    hapticFeedback.confirm()
                    returnResult(voiceUrl)
                } else {
                    ttsManager.speak("上传失败，请重试", TtsManager.Priority.HIGH)
                    hapticFeedback.error()
                    returnResult(null)
                }
            } catch (e: Exception) {
                ttsManager.speak("上传失败：${e.message ?: "网络错误"}", TtsManager.Priority.HIGH)
                hapticFeedback.error()
                returnResult(null)
            } finally {
                file.delete()
            }
        }
    }

    private fun returnResult(voiceUrl: String?) {
        setFragmentResult(REQUEST_KEY, bundleOf(KEY_VOICE_URL to voiceUrl))
        parentFragmentManager.popBackStack()
    }

    private fun stopRecordingAndVibration() {
        maxDurationJob?.cancel()
        vibrationJob?.cancel()
        try {
            recorder?.apply {
                stop()
                release()
            }
        } catch (_: Exception) {
        }
        recorder = null
    }

    companion object {
        const val REQUEST_KEY = "voice_record_result"
        const val KEY_VOICE_URL = "voiceUrl"
    }
}
