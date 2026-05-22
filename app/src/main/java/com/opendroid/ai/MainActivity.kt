package com.opendroid.ai

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import com.opendroid.ai.core.service.OpenDroidService
import com.opendroid.ai.ui.OpenDroidNavigation
import com.opendroid.ai.ui.theme.DarkBackground
import com.opendroid.ai.ui.theme.OpenDroidTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Start foreground assistant service only if RECORD_AUDIO permission is granted
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
            OpenDroidService.start(this)
        }

        setContent {
            OpenDroidTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = DarkBackground
                ) {
                    OpenDroidNavigation()
                }
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        // We want the foreground service to continue running even if UI is destroyed
        // to maintain wake word tracking, but we can stop it if the user wants full quit.
        // For production autonomous helper, we keep the service running in background.
    }
}
