package com.guiderun.app.ui.call

import android.app.KeyguardManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.guiderun.app.R
import com.guiderun.app.service.VoiceCallManager
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class IncomingCallActivity : ComponentActivity() {

    @Inject lateinit var voiceCallManager: VoiceCallManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        showWhenLockedAndTurnScreenOn()

        val fromNickname = intent.getStringExtra(EXTRA_FROM_NICKNAME) ?: "未知用户"
        val requestId = intent.getStringExtra(EXTRA_REQUEST_ID) ?: ""

        setContent {
            MaterialTheme {
                IncomingCallScreen(
                    fromNickname = fromNickname,
                    onAccept = {
                        voiceCallManager.acceptCall()
                        // Navigate to in-call screen or finish
                        finish()
                    },
                    onReject = {
                        voiceCallManager.rejectCall()
                        finish()
                    },
                )
            }
        }
    }

    private fun showWhenLockedAndTurnScreenOn() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
            val km = getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager
            km.requestDismissKeyguard(this, null)
        } else {
            @Suppress("DEPRECATION")
            window.addFlags(
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                    WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON or
                    WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD
            )
        }
    }

    companion object {
        const val EXTRA_FROM_NICKNAME = "from_nickname"
        const val EXTRA_REQUEST_ID = "request_id"

        fun start(context: Context, fromNickname: String, requestId: String) {
            val intent = Intent(context, IncomingCallActivity::class.java).apply {
                putExtra(EXTRA_FROM_NICKNAME, fromNickname)
                putExtra(EXTRA_REQUEST_ID, requestId)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        }
    }
}

@Composable
private fun IncomingCallScreen(
    fromNickname: String,
    onAccept: () -> Unit,
    onReject: () -> Unit,
) {
    Surface(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier.fillMaxSize().padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Text(
                text = stringResource(R.string.incoming_call_title),
                fontSize = 24.sp,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Spacer(Modifier.height(16.dp))
            Text(
                text = fromNickname,
                fontSize = 32.sp,
                color = MaterialTheme.colorScheme.primary,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = stringResource(R.string.incoming_call_subtitle),
                fontSize = 16.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(64.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
            ) {
                Button(
                    onClick = onReject,
                    colors = ButtonDefaults.buttonColors(containerColor = Color.Red),
                    modifier = Modifier.size(72.dp),
                ) {
                    Icon(Icons.Default.Close, contentDescription = stringResource(R.string.incoming_call_reject))
                }
                Button(
                    onClick = onAccept,
                    colors = ButtonDefaults.buttonColors(containerColor = Color.Green),
                    modifier = Modifier.size(72.dp),
                ) {
                    Icon(Icons.Default.Call, contentDescription = stringResource(R.string.incoming_call_accept))
                }
            }
        }
    }
}
