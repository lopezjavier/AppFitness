package io.github.lopezjavier.appfitness

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import io.github.lopezjavier.appfitness.app.AppFeatures
import io.github.lopezjavier.appfitness.app.AppRoot
import io.github.lopezjavier.appfitness.config.AppConfig
import org.koin.android.ext.android.inject

class MainActivity : ComponentActivity() {
    private val appConfig: AppConfig by inject()

    override fun onCreate(savedInstanceState: Bundle?) {
        // The app is always dark: keep light system bar icons even when the device is in light mode.
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
        )
        super.onCreate(savedInstanceState)
        setContent {
            AppRoot(
                features = AppFeatures.from(appConfig),
            )
        }
    }
}
