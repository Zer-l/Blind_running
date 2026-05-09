package com.guiderun.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.compose.rememberNavController
import com.guiderun.app.ui.MainViewModel
import com.guiderun.app.ui.blind.BlindActivity
import com.guiderun.app.ui.navigation.AppNavGraph
import com.guiderun.app.ui.navigation.Screen
import com.guiderun.app.ui.theme.GuideRunTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    private val viewModel: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            GuideRunTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background,
                ) {
                    val startDestination by viewModel.startDestination.collectAsStateWithLifecycle()
                    val navController = rememberNavController()

                    LaunchedEffect(Unit) {
                        viewModel.authEvents.collect {
                            navController.navigate(Screen.Login.route) {
                                popUpTo(0) { inclusive = true }
                            }
                        }
                    }

                    if (startDestination != null) {
                        AppNavGraph(
                            navController = navController,
                            startDestination = startDestination!!,
                            onEnterBlindFlow = { BlindActivity.start(this@MainActivity, BlindActivity.DEST_CREATE_REQUEST) },
                            onEnterBlindSettings = { BlindActivity.start(this@MainActivity, BlindActivity.DEST_SETTINGS) },
                            onEnterBlindHistory = { BlindActivity.start(this@MainActivity, BlindActivity.DEST_HISTORY) },
                        )
                    } else {
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator()
                        }
                    }
                }
            }
        }
    }
}
