package com.guiderun.app.ui.common

import android.widget.Toast
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.PhoneDisabled
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.guiderun.app.R
import com.guiderun.app.util.PhoneDialer

/**
 * 志愿者端 TopAppBar 通用拨号按钮。phone 为 null 时按钮置灰不可点击，
 * 点击时通过 PhoneDialer 直接呼出（未授权 CALL_PHONE 时降级为拨号盘）。
 */
@Composable
fun CallPeerButton(phone: String?) {
    val context = LocalContext.current
    val enabled = !phone.isNullOrBlank()
    val openedDialerHint = stringResource(R.string.call_peer_opened_dialer)
    val failedHint = stringResource(R.string.call_peer_failed)
    IconButton(
        onClick = {
            phone?.let {
                when (PhoneDialer.call(context, it)) {
                    PhoneDialer.Result.OpenedDialer ->
                        Toast.makeText(context, openedDialerHint, Toast.LENGTH_SHORT).show()
                    PhoneDialer.Result.Failed ->
                        Toast.makeText(context, failedHint, Toast.LENGTH_SHORT).show()
                    else -> Unit
                }
            }
        },
        enabled = enabled,
    ) {
        Icon(
            imageVector = if (enabled) Icons.Default.Phone else Icons.Default.PhoneDisabled,
            contentDescription = stringResource(R.string.call_peer_content_description),
            tint = if (enabled) MaterialTheme.colorScheme.primary
            else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f),
        )
    }
}
