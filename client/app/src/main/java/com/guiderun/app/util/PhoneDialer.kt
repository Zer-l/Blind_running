package com.guiderun.app.util

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import androidx.core.content.ContextCompat
import timber.log.Timber

/**
 * 双端统一拨号入口。优先用 ACTION_CALL 直接呼出（CALL_PHONE 已授权时），
 * 未授权时降级为 ACTION_DIAL 跳转拨号盘（号码已填好，需用户手动按拨打），
 * 避免因权限缺失导致按键无反馈。
 */
object PhoneDialer {

    fun call(context: Context, phone: String): Result {
        if (phone.isBlank()) {
            Timber.w("PhoneDialer.call: blank phone")
            return Result.InvalidPhone
        }
        val granted = ContextCompat.checkSelfPermission(
            context, Manifest.permission.CALL_PHONE,
        ) == PackageManager.PERMISSION_GRANTED

        val action = if (granted) Intent.ACTION_CALL else Intent.ACTION_DIAL
        val intent = Intent(action).apply {
            data = Uri.parse("tel:$phone")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        return try {
            context.startActivity(intent)
            if (granted) Result.Calling else Result.OpenedDialer
        } catch (e: Exception) {
            Timber.e(e, "PhoneDialer.call failed: phone=$phone")
            Result.Failed
        }
    }

    sealed interface Result {
        /** ACTION_CALL 成功发起呼叫。 */
        data object Calling : Result
        /** 未授权 CALL_PHONE，已跳转到系统拨号盘（号码已填好）。 */
        data object OpenedDialer : Result
        /** 号码为空，未拨。 */
        data object InvalidPhone : Result
        /** Intent 启动失败（无拨号 App 等）。 */
        data object Failed : Result
    }
}
