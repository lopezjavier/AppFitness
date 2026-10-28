package io.github.lopezjavier.appfitness

import android.Manifest
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import io.github.lopezjavier.appfitness.app.AppFeatures
import io.github.lopezjavier.appfitness.app.AppRoot
import io.github.lopezjavier.appfitness.config.AppConfig
import io.github.lopezjavier.appfitness.platform.AndroidNotificationPermission
import io.github.lopezjavier.appfitness.platform.ProvideAppHaptics
import io.github.lopezjavier.appfitness.platform.ProvideSystemAnimations
import org.koin.android.ext.android.inject

class MainActivity : ComponentActivity() {
    private val appConfig: AppConfig by inject()
    private val notificationPermission: AndroidNotificationPermission by inject()
    private val notificationLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            notificationPermission.onResult(granted)
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        // The app is always dark: keep light system bar icons even when the device is in light mode.
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
        )
        super.onCreate(savedInstanceState)
        notificationPermission.attach { notificationLauncher.launch(Manifest.permission.POST_NOTIFICATIONS) }
        setContent {
            ProvideSystemAnimations {
                ProvideAppHaptics {
                    AppRoot(
                        features = AppFeatures.from(appConfig),
                    )
                }
            }
        }
    }

    override fun onDestroy() {
        notificationPermission.detach()
        super.onDestroy()
    }
}
