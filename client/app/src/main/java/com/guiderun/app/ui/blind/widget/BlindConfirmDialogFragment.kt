package com.guiderun.app.ui.blind.widget

import android.app.Dialog
import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import androidx.annotation.StringRes
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.setFragmentResult
import androidx.lifecycle.lifecycleScope
import com.guiderun.app.R
import com.guiderun.app.accessibility.HapticFeedback
import com.guiderun.app.accessibility.TtsManager
import com.guiderun.app.accessibility.voice.VoiceCommand
import com.guiderun.app.accessibility.voice.bindVoiceCommands
import com.guiderun.app.databinding.FragmentBlindConfirmBinding
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

/**
 * 视障端通用「破坏性操作长按确认」全屏 DialogFragment。
 *
 * 与流程页一致的全屏结构（BlindPageHeader + 上下分割线 + 主按钮吸底 LongPressGestureView）。
 * 用于替代旧的 3 按钮 AlertDialog（继续/返回首页/取消订单），把破坏性操作统一为
 * 「长按 2 秒 + 5 秒倒计时」确认，符合视障端铁律。
 *
 * 使用示例：
 * ```
 * BlindConfirmDialogFragment.newInstance(
 *     requestKey = REQ_KEY,
 *     titleRes = R.string.interrupt_title_leave_create,
 *     messageRes = R.string.interrupt_message_leave_create,
 *     primaryLabelRes = R.string.interrupt_btn_discard,
 *     primaryHintRes = R.string.blind_hint_confirm_discard,
 * ).show(parentFragmentManager, "discard")
 *
 * setFragmentResultListener(REQ_KEY) { _, bundle ->
 *     if (bundle.getBoolean(KEY_CONFIRMED)) doDestructiveAction()
 * }
 * ```
 */
@AndroidEntryPoint
class BlindConfirmDialogFragment : DialogFragment() {

    @Inject lateinit var ttsManager: TtsManager
    @Inject lateinit var hapticFeedback: HapticFeedback

    private var _binding: FragmentBlindConfirmBinding? = null
    private val binding get() = _binding!!

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        isCancelable = true
    }

    /**
     * 构造 Dialog 时把 theme 设为 0 = 继承 Activity 当前主题，
     * 保证 ?attr/blindBackground/blindPrimary 等在 3 套对比度主题下都能正确解析。
     */
    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        return Dialog(requireActivity(), 0)
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        _binding = FragmentBlindConfirmBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onStart() {
        super.onStart()
        dialog?.window?.apply {
            setLayout(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.MATCH_PARENT,
            )
            setBackgroundDrawable(android.graphics.drawable.ColorDrawable(Color.TRANSPARENT))
            setDimAmount(0f)
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val args = requireArguments()
        val titleRes = args.getInt(ARG_TITLE)
        val messageRes = args.getInt(ARG_MESSAGE)
        val primaryLabelRes = args.getInt(ARG_PRIMARY_LABEL)
        val primaryHintRes = args.getInt(ARG_PRIMARY_HINT)
        val thresholdLabelRes = args.getInt(ARG_THRESHOLD_LABEL)
        val cancelledLabelRes = args.getInt(ARG_CANCELLED_LABEL)
        val secondaryLabelRes = args.getInt(ARG_SECONDARY_LABEL)

        binding.header.title = getString(titleRes)
        binding.tvMessage.text = getString(messageRes)

        binding.footer.configure(
            primaryLabelRes = primaryLabelRes,
            primaryHintRes = primaryHintRes,
            secondaryLabelRes = secondaryLabelRes,
        )
        binding.tvActionHint.setText(primaryHintRes)

        binding.footer.primaryGesture.bind(
            scope = viewLifecycleOwner.lifecycleScope,
            ttsManager = ttsManager,
            hapticFeedback = hapticFeedback,
            thresholdLabelRes = thresholdLabelRes,
            countdownLabelRes = cancelledLabelRes,
            onCountdownCommitted = {
                emitResult(confirmed = true)
                dismiss()
            },
            // 5 秒倒计时内松开 = 撤销，留在调用方页面；LongPressGestureView 自身已播"已取消"，
            // 再补播页面身份，避免视障用户在"已取消"后不知道自己在哪
            onCancel = { speakStayedOnHostPage() },
        )

        binding.footer.secondaryButton.setOnClickListener {
            speakStayedOnHostPage()
            emitResult(confirmed = false)
            dismiss()
        }

        setupVoiceCommands()

        // 进入即 TTS 播报标题 + 正文 + 长按提示；warning 震动提醒打断性操作
        hapticFeedback.warning()
        ttsManager.speak(getString(titleRes), TtsManager.Priority.INTERACTION)
        ttsManager.speak(getString(messageRes), TtsManager.Priority.INTERACTION)
        ttsManager.speak(getString(primaryHintRes), TtsManager.Priority.INTERACTION)
    }

    private fun setupVoiceCommands() = bindVoiceCommands { cmd ->
        when (cmd) {
            VoiceCommand.CANCEL -> {
                emitResult(confirmed = false)
                dismiss()
                true
            }
            else -> false
        }
    }

    private fun emitResult(confirmed: Boolean) {
        val requestKey = requireArguments().getString(ARG_REQUEST_KEY) ?: return
        setFragmentResult(requestKey, Bundle().apply { putBoolean(KEY_CONFIRMED, confirmed) })
    }

    /**
     * 弹窗关闭（5 秒倒计时内松开 / 短按继续）时播报"已留在<调用方页面名>"。
     * 调用方在 newInstance 时通过 hostPageTitleRes 传入。
     */
    private fun speakStayedOnHostPage() {
        val hostTitleRes = requireArguments().getInt(ARG_HOST_PAGE_TITLE)
        if (hostTitleRes != 0) {
            ttsManager.speak(
                getString(R.string.blind_tts_stay_on_page, getString(hostTitleRes)),
                TtsManager.Priority.INTERACTION,
            )
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        const val KEY_CONFIRMED = "confirmed"

        private const val ARG_REQUEST_KEY = "request_key"
        private const val ARG_TITLE = "title_res"
        private const val ARG_MESSAGE = "message_res"
        private const val ARG_PRIMARY_LABEL = "primary_label_res"
        private const val ARG_PRIMARY_HINT = "primary_hint_res"
        private const val ARG_THRESHOLD_LABEL = "threshold_label_res"
        private const val ARG_CANCELLED_LABEL = "cancelled_label_res"
        private const val ARG_SECONDARY_LABEL = "secondary_label_res"
        private const val ARG_HOST_PAGE_TITLE = "host_page_title_res"

        fun newInstance(
            requestKey: String,
            @StringRes titleRes: Int,
            @StringRes messageRes: Int,
            @StringRes primaryLabelRes: Int,
            @StringRes primaryHintRes: Int,
            @StringRes thresholdLabelRes: Int,
            @StringRes cancelledLabelRes: Int,
            @StringRes secondaryLabelRes: Int,
            @StringRes hostPageTitleRes: Int = 0,
        ): BlindConfirmDialogFragment = BlindConfirmDialogFragment().apply {
            arguments = Bundle().apply {
                putString(ARG_REQUEST_KEY, requestKey)
                putInt(ARG_TITLE, titleRes)
                putInt(ARG_MESSAGE, messageRes)
                putInt(ARG_PRIMARY_LABEL, primaryLabelRes)
                putInt(ARG_PRIMARY_HINT, primaryHintRes)
                putInt(ARG_THRESHOLD_LABEL, thresholdLabelRes)
                putInt(ARG_CANCELLED_LABEL, cancelledLabelRes)
                putInt(ARG_SECONDARY_LABEL, secondaryLabelRes)
                putInt(ARG_HOST_PAGE_TITLE, hostPageTitleRes)
            }
        }
    }
}
